const user = requireRole('ADMIN');

const PAGE_SIZE = 8;
const ORDER_STATUSES = ['PENDING', 'CONFIRMED', 'READY_FOR_PICKUP', 'COLLECTED', 'CANCELLED'];

// Full (filtered) result sets kept in memory so paging doesn't need a new
// request - these datasets are small enough for a course project that this
// is simpler than real server-side pagination.
let usersAll = [], usersPage = 1;
let productsAll = [], productsPage = 1;
let ordersAll = [], ordersPage = 1;
let approvalsAll = [], approvalsPage = 1;

document.addEventListener('DOMContentLoaded', () => {
    switchTab('overview');
    checkPendingApprovalsBadge();
    document.getElementById('userSearch').addEventListener('input', debounce(loadUsers, 300));
    document.getElementById('userRoleFilter').addEventListener('change', loadUsers);
    document.getElementById('productSearch').addEventListener('input', debounce(loadAllProducts, 300));
    document.getElementById('productCategoryFilter').addEventListener('change', loadAllProducts);
});

// Small red counter on the sidebar link so admin notices new listings
// waiting for review without having to click into the tab.
async function checkPendingApprovalsBadge() {
    const res = await authFetch('/api/admin/products/pending');
    const pending = await res.json();
    const badge = document.getElementById('approvalsBadge');
    if (pending.length > 0) { badge.textContent = pending.length; badge.style.display = 'inline-block'; }
    else { badge.style.display = 'none'; }
}

function debounce(fn, delay) {
    let timer;
    return (...args) => { clearTimeout(timer); timer = setTimeout(() => fn(...args), delay); };
}

function paginate(list, page) {
    const start = (page - 1) * PAGE_SIZE;
    return list.slice(start, start + PAGE_SIZE);
}

// handlerName is the name of a global function like "goToUsersPage" -
// kept as a plain string and wired up via onclick so this one helper works
// for every paginated table/grid on the page.
function renderPagination(containerId, totalItems, currentPage, handlerName) {
    const el = document.getElementById(containerId);
    const totalPages = Math.max(1, Math.ceil(totalItems / PAGE_SIZE));
    if (totalPages <= 1) { el.innerHTML = ''; return; }
    el.innerHTML = `
        <button class="btn btn-outline btn-sm" ${currentPage <= 1 ? 'disabled' : ''} onclick="${handlerName}(${currentPage - 1})">‹ Prev</button>
        <span class="page-info">Page ${currentPage} of ${totalPages}</span>
        <button class="btn btn-outline btn-sm" ${currentPage >= totalPages ? 'disabled' : ''} onclick="${handlerName}(${currentPage + 1})">Next ›</button>`;
}

function switchTab(tab) {
    ['overview', 'approvals', 'users', 'products', 'orders', 'reports', 'settings'].forEach(t => {
        document.getElementById(`tab-${t}`).style.display = (t === tab) ? 'block' : 'none';
    });
    document.querySelectorAll('.side-link').forEach(el => {
        el.classList.toggle('active', el.dataset.tab === tab);
    });

    if (tab === 'overview') loadOverview();
    if (tab === 'approvals') loadApprovals();
    if (tab === 'users') loadUsers();
    if (tab === 'products') loadAllProducts();
    if (tab === 'orders') loadAllOrders();
    if (tab === 'reports') loadReports();
    if (tab === 'settings') loadSettings();
}

// ---------------- Overview ----------------

async function loadOverview() {
    const res = await authFetch('/api/admin/overview');
    const data = await res.json();

    document.getElementById('statRow').innerHTML = `
        <div class="stat-card"><div class="stat-num">${data.totalUsers}</div><div class="stat-label">Total Users</div></div>
        <div class="stat-card"><div class="stat-num">${data.totalProducts}</div><div class="stat-label">Total Products</div></div>
        <div class="stat-card"><div class="stat-num">${data.totalOrders}</div><div class="stat-label">Total Orders</div></div>
        <div class="stat-card" style="cursor:pointer;" onclick="switchTab('approvals')"><div class="stat-num">${data.pendingApprovals || 0}</div><div class="stat-label">Pending Approvals</div></div>
    `;
    checkPendingApprovalsBadge();

    const cats = Object.entries(data.categoryCounts || {});
    const maxCount = Math.max(1, ...cats.map(([, c]) => c));
    document.getElementById('categoryChart').innerHTML = cats.length
        ? cats.map(([name, count]) => `
            <div class="admin-chart-row">
                <span>${name}</span>
                <div class="admin-chart-track"><div class="admin-chart-fill" style="width:${(count / maxCount * 100).toFixed(0)}%"></div></div>
                <span>${count}</span>
            </div>`).join('')
        : '<p class="admin-empty-row">No products yet.</p>';

    document.getElementById('recentUsers').innerHTML = (data.recentUsers || []).length
        ? data.recentUsers.map(u => `<div class="admin-mini-row"><span>${escapeHtml(u.name)}</span><small>${escapeHtml(u.email)}</small></div>`).join('')
        : '<div class="admin-empty-row">No signups yet.</div>';

    document.getElementById('recentOrders').innerHTML = (data.recentOrders || []).length
        ? data.recentOrders.map(o => `<div class="admin-mini-row"><span>Order #${o.id}</span><small>${o.status}</small></div>`).join('')
        : '<div class="admin-empty-row">No orders yet.</div>';
}

// ---------------- Users ----------------

async function loadUsers() {
    const params = new URLSearchParams();
    const q = document.getElementById('userSearch').value.trim();
    const role = document.getElementById('userRoleFilter').value;
    if (q) params.set('query', q);
    if (role) params.set('role', role);

    const res = await authFetch(`/api/admin/users?${params.toString()}`);
    usersAll = await res.json();
    usersPage = 1;
    renderUsersPage();
}

function goToUsersPage(page) { usersPage = page; renderUsersPage(); }

function renderUsersPage() {
    const body = document.getElementById('usersBody');
    const items = paginate(usersAll, usersPage);
    body.innerHTML = items.length ? '' : `<tr><td colspan="5" class="admin-empty-row">No users match.</td></tr>`;

    items.forEach(u => {
        const suspended = u.status === 'SUSPENDED';
        const suspendBtn = u.role === 'ADMIN' ? '' :
            `<button class="btn btn-sm ${suspended ? 'btn-primary' : 'btn-danger'}" onclick="toggleSuspend(${u.id})">${suspended ? 'Reinstate' : 'Suspend'}</button>`;
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${escapeHtml(u.name)}</td>
            <td>${escapeHtml(u.email)}</td>
            <td>${u.role}</td>
            <td><span class="status-pill status-${(u.status || 'approved').toLowerCase()}">${u.status || 'APPROVED'}</span></td>
            <td class="admin-actions-cell">
                <button class="btn btn-outline btn-sm" onclick="viewUserDetails(${u.id})">Details</button>
                <button class="text-button" onclick="toggleVerify(${u.id})">${u.verified ? 'Remove verification' : 'Verify'}</button>
                ${suspendBtn}
            </td>`;
        body.appendChild(row);
    });

    renderPagination('usersPagination', usersAll.length, usersPage, 'goToUsersPage');
}

async function toggleVerify(id) { await authFetch(`/api/admin/users/${id}/verify`, { method: 'PUT' }); loadUsers(); }

async function toggleSuspend(id) {
    if (!confirm('Change this user\'s suspension status? A suspended user is logged out immediately and cannot log back in.')) return;
    const res = await authFetch(`/api/admin/users/${id}/suspend`, { method: 'PUT' });
    if (!res.ok) { const d = await res.json().catch(() => ({})); alert(d.message || 'Could not update this user.'); return; }
    loadUsers();
}

async function viewUserDetails(id) {
    const res = await authFetch(`/api/admin/users/${id}/details`);
    if (!res.ok) { alert('Could not load user details.'); return; }
    const data = await res.json();

    document.getElementById('userModalName').textContent = `${data.user.name} — ${data.user.email}`;

    const listRow = (label, sub) => `<div class="admin-mini-row"><span>${escapeHtml(label)}</span><small>${sub}</small></div>`;
    const listingsHtml = data.listings.length
        ? data.listings.map(p => listRow(p.name, `৳${p.price} · ${p.availability}`)).join('')
        : '<div class="admin-empty-row">No listings.</div>';
    const purchasesHtml = data.purchases.length
        ? data.purchases.map(o => listRow(`Order #${o.id} (product #${o.productId})`, o.status)).join('')
        : '<div class="admin-empty-row">No purchases.</div>';
    const salesHtml = data.sales.length
        ? data.sales.map(o => listRow(`Order #${o.id} (product #${o.productId})`, o.status)).join('')
        : '<div class="admin-empty-row">No sales.</div>';

    document.getElementById('userModalBody').innerHTML = `
        <p class="text-muted">Role: ${data.user.role} · Status: ${data.user.status || 'APPROVED'}</p>
        <h3>Listings (${data.listings.length})</h3><div class="admin-mini-list">${listingsHtml}</div>
        <h3>Purchases (${data.purchases.length})</h3><div class="admin-mini-list">${purchasesHtml}</div>
        <h3>Sales (${data.sales.length})</h3><div class="admin-mini-list">${salesHtml}</div>
    `;
    document.getElementById('userModal').classList.add('show');
}

function closeUserModal() { document.getElementById('userModal').classList.remove('show'); }

// ---------------- Products ----------------

async function loadAllProducts() {
    const params = new URLSearchParams();
    const q = document.getElementById('productSearch').value.trim();
    const cat = document.getElementById('productCategoryFilter').value;
    if (q) params.set('query', q);
    if (cat) params.set('category', cat);

    const res = await authFetch(`/api/admin/products?${params.toString()}`);
    productsAll = await res.json();
    productsPage = 1;
    renderProductsPage();
}

function goToProductsPage(page) { productsPage = page; renderProductsPage(); }

function renderProductsPage() {
    const grid = document.getElementById('allProductsGrid');
    const items = paginate(productsAll, productsPage);
    grid.innerHTML = items.length ? '' : `<p class="admin-empty-row">No products match.</p>`;

    items.forEach(p => {
        const card = document.createElement('div');
        card.className = 'card';
        card.innerHTML = `
            ${p.imageUrl ? `<img class="card-img" src="${escapeHtml(p.imageUrl)}" alt="${escapeHtml(p.name)}">` : `<div class="card-img placeholder">🛒</div>`}
            <div class="card-body">
                <div class="card-cat">${escapeHtml(p.category || 'General')}</div>
                <h3 class="card-title">${escapeHtml(p.name)}</h3>
                <div class="card-foot">
                    <span class="price">${p.price}</span>
                    <span class="stock-tag">${p.stock} in stock</span>
                </div>
                <div class="admin-actions-cell" style="margin-top:10px;">
                    <button class="btn btn-outline btn-sm" style="flex:1" onclick="toggleVisibility(${p.id})">${p.availability === 'HIDDEN' ? 'Show listing' : 'Hide listing'}</button>
                    <button class="btn btn-danger btn-sm" style="flex:1" onclick="deleteProductAdmin(${p.id})">Delete</button>
                </div>
            </div>`;
        grid.appendChild(card);
    });

    renderPagination('productsPagination', productsAll.length, productsPage, 'goToProductsPage');
}

async function toggleVisibility(id) { await authFetch(`/api/admin/products/${id}/visibility`, { method: 'PUT' }); loadAllProducts(); }

async function deleteProductAdmin(id) {
    if (!confirm('Permanently delete this listing? This cannot be undone.')) return;
    const res = await authFetch(`/api/admin/products/${id}`, { method: 'DELETE' });
    if (!res.ok) { const d = await res.json().catch(() => ({})); alert(d.message || 'Could not delete this product.'); return; }
    loadAllProducts();
}

// ---------------- Pending Approvals ----------------

async function loadApprovals() {
    const res = await authFetch('/api/admin/products/pending');
    approvalsAll = await res.json();
    approvalsPage = 1;
    renderApprovalsPage();
    checkPendingApprovalsBadge();
}

function goToApprovalsPage(page) { approvalsPage = page; renderApprovalsPage(); }

function renderApprovalsPage() {
    const grid = document.getElementById('approvalsGrid');
    const items = paginate(approvalsAll, approvalsPage);
    grid.innerHTML = items.length ? '' : `<p class="admin-empty-row">Nothing waiting for review right now.</p>`;

    items.forEach(p => {
        const card = document.createElement('div');
        card.className = 'card admin-approval-card';
        card.innerHTML = `
            ${p.imageUrl ? `<img class="card-img" src="${escapeHtml(p.imageUrl)}" alt="${escapeHtml(p.name)}">` : `<div class="card-img placeholder">🛒</div>`}
            <div class="card-body">
                <div class="card-cat">${escapeHtml(p.category || 'General')}</div>
                <h3 class="card-title">${escapeHtml(p.name)}</h3>
                <p class="text-muted" style="font-size:.82rem;">${escapeHtml((p.description || '').slice(0, 120))}${(p.description || '').length > 120 ? '…' : ''}</p>
                <div class="card-foot">
                    <span class="price">${p.price}</span>
                    <span class="stock-tag">${p.stock} in stock · seller #${p.sellerId}</span>
                </div>
                <div class="admin-actions-cell" style="margin-top:10px;">
                    <button class="btn btn-primary btn-sm" style="flex:1" onclick="approveProduct(${p.id})">Approve</button>
                    <button class="btn btn-danger btn-sm" style="flex:1" onclick="rejectProduct(${p.id})">Reject</button>
                </div>
            </div>`;
        grid.appendChild(card);
    });

    renderPagination('approvalsPagination', approvalsAll.length, approvalsPage, 'goToApprovalsPage');
}

async function approveProduct(id) {
    if (!confirm('Approve this listing? It will become visible to everyone.')) return;
    const res = await authFetch(`/api/admin/products/${id}/approve`, { method: 'PUT' });
    if (!res.ok) { const d = await res.json().catch(() => ({})); alert(d.message || 'Could not approve this listing.'); return; }
    loadApprovals();
}

async function rejectProduct(id) {
    const reason = prompt('Why is this listing being rejected? (shown to the seller)');
    if (reason === null) return; // cancelled
    const res = await authFetch(`/api/admin/products/${id}/reject`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason })
    });
    if (!res.ok) { const d = await res.json().catch(() => ({})); alert(d.message || 'Could not reject this listing.'); return; }
    loadApprovals();
}

// ---------------- Orders ----------------

async function loadAllOrders() {
    const res = await authFetch('/api/admin/orders');
    ordersAll = await res.json();
    ordersPage = 1;
    renderOrdersPage();
}

function goToOrdersPage(page) { ordersPage = page; renderOrdersPage(); }

function renderOrdersPage() {
    const body = document.getElementById('allOrdersBody');
    const items = paginate(ordersAll, ordersPage);
    body.innerHTML = items.length ? '' : `<tr><td colspan="7" class="admin-empty-row">No orders yet.</td></tr>`;

    items.forEach(o => {
        const selectId = `orderStatus-${o.id}`;
        const options = ORDER_STATUSES.map(s => `<option value="${s}" ${s === o.status ? 'selected' : ''}>${s}</option>`).join('');
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>#${o.id}</td>
            <td>${o.buyerId}</td>
            <td>${o.productId}</td>
            <td>${o.quantity}</td>
            <td><span class="status-pill status-${o.status.toLowerCase()}">${o.status}</span></td>
            <td>${new Date(o.orderDate).toLocaleDateString()}</td>
            <td class="admin-order-action">
                <select id="${selectId}">${options}</select>
                <button class="btn btn-outline btn-sm" onclick="applyOrderStatus(${o.id})">Update</button>
            </td>`;
        body.appendChild(row);
    });

    renderPagination('ordersPagination', ordersAll.length, ordersPage, 'goToOrdersPage');
}

// Lets admin override a stuck/disputed order to any status (see AdminController#setOrderStatus) -
// unlike the normal seller flow, this isn't limited to the next step in the pickup sequence.
async function applyOrderStatus(id) {
    const select = document.getElementById(`orderStatus-${id}`);
    const next = select.value;
    if (!confirm(`Change order #${id} to ${next}? Both buyer and seller will be notified.`)) return;
    const res = await authFetch(`/api/admin/orders/${id}/status`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: next })
    });
    if (!res.ok) { const d = await res.json().catch(() => ({})); alert(d.message || 'Could not update this order.'); return; }
    loadAllOrders();
}

// ---------------- Reports ----------------

async function loadReports() {
    const [reportsRes, productsRes] = await Promise.all([
        authFetch('/api/reports'),
        authFetch('/api/admin/products')
    ]);
    const reports = await reportsRes.json();
    const products = await productsRes.json();
    const nameById = {};
    products.forEach(p => { nameById[p.id] = p.name; });

    const body = document.getElementById('reportsBody');
    body.innerHTML = reports.length ? '' : `<tr><td colspan="5" class="admin-empty-row">No reports.</td></tr>`;

    reports.forEach(r => {
        const label = nameById[r.productId] ? `${escapeHtml(nameById[r.productId])} (#${r.productId})` : `#${r.productId}`;
        const actions = [];
        if (r.status === 'OPEN') {
            actions.push(`<button class="btn btn-outline btn-sm" onclick="toggleVisibility(${r.productId})">Hide listing</button>`);
            actions.push(`<button class="btn btn-outline btn-sm" onclick="resolveReport(${r.id})">Resolve</button>`);
        }
        actions.push(`<button class="btn btn-danger btn-sm" onclick="deleteReport(${r.id})">Delete</button>`);

        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${label}</td>
            <td>${escapeHtml(r.reason)}</td>
            <td>${escapeHtml(r.details || '-')}</td>
            <td><span class="status-pill status-${r.status.toLowerCase()}">${r.status}</span></td>
            <td class="admin-actions-cell">${actions.join('')}</td>`;
        body.appendChild(row);
    });
}

async function resolveReport(id) { await authFetch(`/api/reports/${id}/resolve`, { method: 'PUT' }); loadReports(); }

async function deleteReport(id) {
    if (!confirm('Delete this report? This just removes the report record, not the listing.')) return;
    await authFetch(`/api/reports/${id}`, { method: 'DELETE' });
    loadReports();
}

// ---------------- Pickup settings ----------------

async function loadSettings() {
    const res = await fetch('/api/marketplace/settings');
    const s = await res.json();
    pickupPoint.value = s.pickupPoint;
    pickupInstructions.value = s.pickupInstructions || '';
}

document.getElementById('settingsForm').addEventListener('submit', async event => {
    event.preventDefault();
    const res = await authFetch('/api/marketplace/settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ pickupPoint: pickupPoint.value, pickupInstructions: pickupInstructions.value })
    });
    if (!res.ok) return alert('Could not save pickup settings.');
    alert('Pickup settings saved.');
});
