// Categories shown as icon tiles on the home page. Each links into categories.html
// with that category pre-selected. Feel free to add more.
const CATEGORIES = [
    { name: 'Electronics', icon: '💻' },
    { name: 'Fashion', icon: '👕' },
    { name: 'Home', icon: '🛋️' },
    { name: 'Books', icon: '📚' },
    { name: 'Sports', icon: '⚽' },
    { name: 'Other', icon: '🧩' }
];

let wishlistIds = [];

document.addEventListener('DOMContentLoaded', () => {
    buildCategoryTiles();
    loadWishlistIds().then(loadFeatured);
});

function buildCategoryTiles() {
    const wrap = document.getElementById('categoryChips');
    wrap.innerHTML = CATEGORIES.map(c => `
        <a class="cat-item" href="categories.html?category=${encodeURIComponent(c.name)}">
            <span class="cat-icon">${c.icon}</span>${c.name}
        </a>
    `).join('') + `
        <a class="cat-item cat-sell" href="seller-dashboard.html">
            <span class="cat-icon">＋</span>Sell an item
        </a>
    `;
}

// Buyer's wishlist product IDs, so we know which hearts to fill in.
async function loadWishlistIds() {
    const user = getUser();
    if (!user || user.role === 'ADMIN') return;
    try {
        const res = await authFetch('/api/wishlist');
        if (res.ok) {
            const products = await res.json();
            wishlistIds = products.map(p => p.id);
        }
    } catch (e) { /* not logged in or error - just show empty hearts */ }
}

async function loadFeatured() {
    const res = await fetch('/api/products?sort=newest&page=1&size=8');
    const data = await res.json();
    renderProducts(data.products);
}

function searchProducts() {
    const keyword = document.getElementById('searchInput').value.trim();
    window.location.href = 'categories.html' + (keyword ? '?keyword=' + encodeURIComponent(keyword) : '');
}

async function toggleWishlist(productId, event) {
    event.preventDefault();
    event.stopPropagation();
    const user = getUser();
    if (!user) {
        window.location.href = 'login.html';
        return;
    }
    if (wishlistIds.includes(productId)) {
        await authFetch(`/api/wishlist/${productId}`, { method: 'DELETE' });
        wishlistIds = wishlistIds.filter(id => id !== productId);
    } else {
        await authFetch(`/api/wishlist/${productId}`, { method: 'POST' });
        wishlistIds.push(productId);
    }
    const heart = document.getElementById(`heart-${productId}`);
    if (heart) {
        const active = wishlistIds.includes(productId);
        heart.classList.toggle('active', active);
        heart.textContent = active ? '♥' : '♡';
    }
}

function renderProducts(products) {
    const grid = document.getElementById('productGrid');
    const empty = document.getElementById('emptyState');
    const user = getUser();
    const showWishlist = user && user.role !== 'ADMIN';

    if (!products.length) {
        grid.innerHTML = '';
        empty.style.display = 'block';
        return;
    }
    empty.style.display = 'none';
    grid.innerHTML = products.map(p => productCardHTML(p, wishlistIds, showWishlist)).join('');
}
