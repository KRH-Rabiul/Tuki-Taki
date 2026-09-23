// ---------- Shared helpers used on every page ----------

// Escapes user-supplied text before it's dropped into innerHTML, so a
// product name, bio, or chat message containing '<' or '&' can't break the
// page or inject markup. Every page that renders user-entered text should
// run it through this first.
function escapeHtml(s) {
    return String(s ?? '').replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
}

// A small blue checkmark badge next to a seller/user's name, the same idea as
// Facebook's verified tick. Used anywhere a name is shown alongside a
// `verified` boolean from the API (seller cards, shop pages, messages, community).
function verifiedBadgeHTML(isVerified) {
    return isVerified
        ? '<span class="verified-badge" title="Verified seller">✓</span>'
        : '';
}

// Save the logged-in user (id, name, email, role) in the browser after login.
function saveUser(user) {
    localStorage.setItem('mb_user', JSON.stringify(user));
}

// Read the logged-in user back. Returns null if nobody is logged in.
function getUser() {
    const raw = localStorage.getItem('mb_user');
    return raw ? JSON.parse(raw) : null;
}

// Read just the login token back out of the saved user.
function getToken() {
    const user = getUser();
    return user ? user.token : null;
}

async function logout() {
    const token = getToken();
    if (token) {
        try {
            await fetch('/api/auth/logout', {
                method: 'POST',
                headers: { 'Authorization': 'Bearer ' + token }
            });
        } catch (e) { /* ignore - clearing locally either way */ }
    }
    localStorage.removeItem('mb_user');
    window.location.href = 'login.html';
}

// Same as fetch(), but automatically attaches the login token so protected
// /api/... routes accept the request. Use this instead of plain fetch()
// for anything except public browsing, register, and login.
async function authFetch(url, options = {}) {
    const token = getToken();
    options.headers = options.headers || {};
    if (token) options.headers['Authorization'] = 'Bearer ' + token;
    return fetch(url, options);
}

// Call this at the top of a dashboard page to make sure only the
// correct role can see it. Redirects to login if not allowed.
function requireRole(role) {
    const user = getUser();
    if (!user || user.role !== role) {
        window.location.href = 'login.html';
    }
    return user;
}

function requireMarketplaceUser() {
    const user = getUser();
    if (!user || user.role === 'ADMIN') {
        window.location.href = 'login.html';
    }
    return user;
}

// Fills in the navbar name + role badge + avatar initial if those elements exist on the page.
// Shows the right thing in the top-right of the navbar depending on whether
// someone is logged in: their name/avatar/role if so, or the Log in / Sign up
// buttons if not. Called on every page (see the DOMContentLoaded listener at
// the bottom of this file), so every page's navbar stays in sync automatically -
// no page needs to duplicate this logic itself.
function paintNavUser() {
    const user = getUser();
    const guestLinks = document.getElementById('guestLinks');
    const userLinks = document.getElementById('userLinks');
    if (guestLinks && userLinks) {
        guestLinks.style.display = user ? 'none' : 'flex';
        userLinks.style.display = user ? 'flex' : 'none';
    }
    const nameEl = document.getElementById('navUserName');
    const roleEl = document.getElementById('navUserRole');
    const avatarEl = document.getElementById('navAvatar');
    if (user && nameEl) nameEl.textContent = user.name;
    if (user && roleEl) roleEl.textContent = user.role;
    if (user && avatarEl) {
        avatarEl.innerHTML = user.profileImageUrl
            ? `<img src="${escapeHtml(user.profileImageUrl)}" alt="Profile photo">`
            : escapeHtml((user.name || '?').trim().charAt(0).toUpperCase());
        avatarEl.classList.toggle('has-photo', !!user.profileImageUrl);
    }
    if (user && user.role === 'ADMIN') hideShopperNavLinks();
    updateCartCount();
    if (user) updateNotificationBadge();
}

async function updateNotificationBadge() {
    const badge = document.getElementById('notificationBadge');
    if (!badge || !getUser() || getUser().role === 'ADMIN') return;
    try {
        const res = await authFetch('/api/notifications/unread-count');
        if (!res.ok) return;
        const data = await res.json();
        badge.textContent = data.count || 0;
        badge.style.display = data.count > 0 ? 'inline-flex' : 'none';
    } catch (e) { /* notification badge is non-critical */ }
}

// Admins don't buy/sell, so the Cart/Sell/Wishlist nav links (and the matching
// menu items) don't apply to them - swap in a single Admin Panel link instead.
function hideShopperNavLinks() {
    document.querySelectorAll('.nav-links a[href="cart.html"], .nav-links a[href="seller-dashboard.html"], .nav-links a[href="wishlist.html"]').forEach(a => a.remove());
    document.querySelectorAll('.user-menu-panel a[href="buyer-dashboard.html"], .user-menu-panel a[href="seller-dashboard.html"]').forEach(a => a.remove());
    document.querySelectorAll('.nav-links').forEach(nav => {
        if (!nav.querySelector('a[href="admin-dashboard.html"]')) {
            const link = document.createElement('a');
            link.href = 'admin-dashboard.html';
            link.textContent = 'Admin Panel';
            nav.appendChild(link);
        }
    });
}

// ---------- Cart storage (per logged-in user, so two accounts on the same
// browser never see each other's cart) ----------

// Each user gets their own cart bucket in localStorage, keyed by their id.
// A visitor who isn't logged in gets a separate "guest" bucket.
function cartKey() {
    const user = getUser();
    return user ? ('mb_cart_' + user.id) : 'mb_cart_guest';
}

function getCart() {
    try {
        return JSON.parse(localStorage.getItem(cartKey()) || '[]');
    } catch (e) {
        return [];
    }
}

function saveCart(cart) {
    localStorage.setItem(cartKey(), JSON.stringify(cart));
    updateCartCount();
}

// If someone added things to their cart before logging in, fold those items
// into their account's cart right after login instead of losing them.
function mergeGuestCartIntoUser() {
    const guestCart = (() => {
        try { return JSON.parse(localStorage.getItem('mb_cart_guest') || '[]'); }
        catch (e) { return []; }
    })();
    if (!guestCart.length) return;

    const userCart = getCart();
    guestCart.forEach(item => {
        const existing = userCart.find(i => i.productId === item.productId);
        if (existing) existing.quantity += item.quantity;
        else userCart.push(item);
    });
    saveCart(userCart);
    localStorage.removeItem('mb_cart_guest');
}

// This just paints the badge on whichever page happens to have a #cartBadge
// element in its navbar - the cart data itself lives in getCart()/saveCart().
function updateCartCount() {
    const badge = document.getElementById('cartBadge');
    if (!badge) return;
    const count = getCart().reduce((sum, item) => sum + (item.quantity || 1), 0);
    badge.textContent = count;
    badge.style.display = count > 0 ? 'inline-flex' : 'none';
}

// One product card, shared by the home page and the category/browse page so
// both stay visually identical. wishlistIds lets us pre-fill hearts.
function productCardHTML(p, wishlistIds, showWishlist) {
    const conditionLabel = { NEW: 'New', LIKE_NEW: 'Like New', GOOD: 'Good', USED: 'Used' }[p.condition] || '';
    const safeName = escapeHtml(p.name);
    const safeCategory = escapeHtml(p.category || 'General');
    return `
        <a href="product-details.html?id=${p.id}" class="card">
            <div class="card-img-wrap">
                ${p.imageUrl ? `<img class="card-img" src="${escapeHtml(p.imageUrl)}" alt="${safeName}">` : `<div class="card-img placeholder">🛍️</div>`}
                ${conditionLabel ? `<span class="condition-tag">${conditionLabel}</span>` : ''}
                ${showWishlist ? `<span id="heart-${p.id}" class="wishlist-heart ${wishlistIds.includes(p.id) ? 'active' : ''}" onclick="toggleWishlist(${p.id}, event)">${wishlistIds.includes(p.id) ? '♥' : '♡'}</span>` : ''}
            </div>
            <div class="card-body">
                <div class="card-cat">${safeCategory}</div>
                <h3 class="card-title">${safeName}</h3>
                <div class="card-foot">
                    <span class="price">${p.price}</span>
                    <span class="stock-tag">${p.stock > 0 ? p.stock + ' left' : 'Out of stock'}</span>
                </div>
            </div>
        </a>
    `;
}

// A nicer "order placed" confirmation than a plain text alert - shows the
// actual pickup point (Bonomaya) so buyers know exactly where to go.
function pickupSuccessCardHTML(message) {
    return `
        <div class="pickup-success-card">
            <img src="images/bonomaya-pickup-point.jpg" alt="Bonomaya pickup point">
            <div class="pickup-success-body">
                <span class="tag tag-mint">✓ Order placed</span>
                <h4>Collect from Bonomaya</h4>
                <p>${message}</p>
            </div>
        </div>
    `;
}

function getThemePreference() {
    return localStorage.getItem('mb_theme') || 'system';
}

function applyTheme(preference = getThemePreference()) {
    const systemIsDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const resolved = preference === 'system' ? (systemIsDark ? 'dark' : 'light') : preference;
    document.documentElement.dataset.theme = resolved;
    document.documentElement.style.colorScheme = resolved;
    document.querySelectorAll('.theme-switch').forEach(btn => btn.setAttribute('aria-checked', resolved === 'dark'));
}

// A simple sliding switch (☀ / ☾) instead of a dropdown - click toggles
// straight between light and dark. Starts from the system preference the
// first time a person visits, then remembers whatever they picked.
function setupThemeControl() {
    applyTheme();
    document.querySelectorAll('.navbar .container').forEach(nav => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'theme-switch';
        btn.setAttribute('role', 'switch');
        btn.setAttribute('aria-label', 'Toggle dark mode');
        btn.innerHTML = `<span class="theme-switch-thumb" aria-hidden="true">☀</span>`;
        btn.addEventListener('click', () => {
            const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
            localStorage.setItem('mb_theme', next);
            applyTheme(next);
            btn.querySelector('.theme-switch-thumb').textContent = next === 'dark' ? '☾' : '☀';
        });
        btn.querySelector('.theme-switch-thumb').textContent = document.documentElement.dataset.theme === 'dark' ? '☾' : '☀';
        nav.appendChild(btn);
    });
}

// Wires up the little "click avatar to open a menu" dropdown wherever a page
// has a .user-menu wrapper (button.user-chip + .user-menu-panel inside it).
function setupUserMenu() {
    const user = getUser();
    document.querySelectorAll('.user-menu').forEach(menu => {
        const trigger = menu.querySelector('.user-chip');
        const panel = menu.querySelector('.user-menu-panel');
        if (!trigger || !panel) return;
        if (user && user.role !== 'ADMIN') {
            const links = [
                ['profile.html','My Profile'],
                ['notifications.html','Notifications <span class="nav-badge" id="notificationBadge" style="display:none">0</span>'],
                ['buyer-dashboard.html','My Orders'],
                ['seller-shop.html','My Shop'],
                ['messages.html','Messages']
            ];
            links.slice().reverse().forEach(([href,label]) => {
                if (!panel.querySelector(`a[href="${href}"]`)) {
                    const a=document.createElement('a'); a.href=href; a.innerHTML=label; panel.insertBefore(a,panel.firstChild);
                }
            });
            panel.querySelectorAll('a').forEach(a=>{if(a.textContent.trim()==='My Listings') a.textContent='My Shop';});

            // A page may already ship its own plain "Notifications" link (no
            // badge). Whether that link was already there or just inserted
            // above, make sure it always has the unread-count badge.
            const notifLink = panel.querySelector('a[href="notifications.html"]');
            if (notifLink && !notifLink.querySelector('#notificationBadge')) {
                notifLink.innerHTML = 'Notifications <span class="nav-badge" id="notificationBadge" style="display:none">0</span>';
            }
        }
        trigger.addEventListener('click', (e) => {
            e.stopPropagation();
            panel.classList.toggle('open');
        });
    });
    // All My Shop links should open the logged-in seller's public shop, not the
    // seller management dashboard. The id is injected from the authenticated user.
    if (user && user.role !== 'ADMIN') {
        document.querySelectorAll('a').forEach(a => {
            if (a.textContent.trim() === 'My Shop' || a.hasAttribute('data-my-shop-link')) {
                a.href = 'seller-shop.html?id=' + encodeURIComponent(user.id);
            }
        });
    }
    if (user && user.role !== 'ADMIN') updateNotificationBadge();
    document.addEventListener('click', () => {
        document.querySelectorAll('.user-menu-panel.open').forEach(p => p.classList.remove('open'));
    });
}

window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (getThemePreference() === 'system') applyTheme('system');
});

document.addEventListener('DOMContentLoaded', () => {
    paintNavUser();
    setupThemeControl();
    setupUserMenu();
});
