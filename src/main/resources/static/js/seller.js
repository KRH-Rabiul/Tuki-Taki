const user = requireMarketplaceUser();
let editingProductId = null;
let selectedImageFiles = [];
let currentProducts = []; // keeps the last loaded product list so the Edit button can look items up by id

document.addEventListener('DOMContentLoaded', () => {
    loadProducts();
    // Lets sidebar links from OTHER pages (e.g. Profile, Notifications) jump
    // straight to the Orders tab via seller-dashboard.html#orders.
    if (location.hash === '#orders') switchTab('orders');
});

function switchTab(tab) {
    document.getElementById('tab-products').style.display = tab === 'products' ? 'block' : 'none';
    document.getElementById('tab-orders').style.display = tab === 'orders' ? 'block' : 'none';
    document.querySelectorAll('.side-link').forEach(el => {
        el.classList.toggle('active', el.dataset.tab === tab);
    });
    if (tab === 'orders') loadOrders();
}

/* ---------------- Products ---------------- */

async function loadProducts() {
    const res = await fetch(`/api/products/seller/${user.id}`);
    const products = await res.json();
    currentProducts = products;

    const body = document.getElementById('productTableBody');
    const empty = document.getElementById('emptyProducts');
    body.innerHTML = '';

    if (!products.length) {
        empty.style.display = 'block';
        return;
    }
    empty.style.display = 'none';

    products.forEach(p => {
        const row = document.createElement('tr');
        const status = p.availability || 'ACTIVE';
        const statusClass = status.toLowerCase().replace(/_/g, '-');
        const rejectionNote = (status === 'REJECTED' && p.rejectionReason)
            ? `<br><small class="text-muted" title="${p.rejectionReason}">Reason: ${p.rejectionReason}</small>` : '';
        const pendingNote = status === 'PENDING_REVIEW'
            ? `<br><small class="text-muted">Waiting for admin approval</small>` : '';
        row.innerHTML = `
            <td>${p.imageUrl ? `<img class="table-thumb" src="${p.imageUrl}" alt="">` : `<div class="table-thumb" style="display:flex;align-items:center;justify-content:center;">🛍️</div>`}</td>
            <td><strong>${escapeHtml(p.name)}</strong><br><span class="text-muted" style="font-size:.78rem;">${escapeHtml(p.category || 'General')}</span></td>
            <td>${p.price}</td>
            <td>${p.stock}</td>
            <td><span class="status-pill status-${statusClass}">${status.replace('_', ' ')}</span>${rejectionNote}${pendingNote}</td>
            <td>
                <button class="btn btn-outline btn-sm" onclick="openEditModalById(${p.id})">Edit</button>
                <button class="btn btn-danger btn-sm" onclick="deleteProduct(${p.id})">Delete</button>
            </td>
        `;
        body.appendChild(row);
    });
}

function openEditModalById(id) {
    const product = currentProducts.find(p => p.id === id);
    if (product) openEditModal(product);
}

function openAddModal() {
    editingProductId = null;
    selectedImageFiles = [];
    document.getElementById('modalTitle').textContent = 'Add Product';
    document.getElementById('productForm').reset();
    document.getElementById('imagePreview').style.display = 'none';
    document.getElementById('imageHint').style.display = 'block';
    document.getElementById('productError').classList.remove('show');
    document.getElementById('productModal').classList.add('show');
}

function openEditModal(p) {
    editingProductId = p.id;
    selectedImageFiles = [];
    document.getElementById('modalTitle').textContent = 'Edit Product';
    document.getElementById('pName').value = p.name;
    document.getElementById('pCategory').value = p.category || 'Other';
    document.getElementById('pDescription').value = p.description || '';
    document.getElementById('pPrice').value = p.price;
    document.getElementById('pStock').value = p.stock;
    document.getElementById('pCondition').value = p.condition || 'USED';
    document.getElementById('pAvailability').value = p.availability || 'ACTIVE';
    document.getElementById('pPickupWindow').value = p.pickupWindow || '';

    const preview = document.getElementById('imagePreview');
    if (p.imageUrl) {
        preview.src = p.imageUrl;
        preview.style.display = 'block';
        document.getElementById('imageHint').style.display = 'none';
    } else {
        preview.style.display = 'none';
        document.getElementById('imageHint').style.display = 'block';
    }

    document.getElementById('productError').classList.remove('show');
    document.getElementById('productModal').classList.add('show');
}

function closeModal() {
    document.getElementById('productModal').classList.remove('show');
}

function previewImage(input) {
    if (input.files && input.files[0]) {
        selectedImageFiles = Array.from(input.files).slice(0, 5);
        const reader = new FileReader();
        reader.onload = (e) => {
            const preview = document.getElementById('imagePreview');
            preview.src = e.target.result;
            preview.style.display = 'block';
            document.getElementById('imageHint').style.display = 'none';
        };
        reader.readAsDataURL(selectedImageFiles[0]);
    }
}

document.getElementById('productForm').addEventListener('submit', async (e) => {
    e.preventDefault();

    const formData = new FormData();
    formData.append('name', document.getElementById('pName').value);
    formData.append('description', document.getElementById('pDescription').value);
    formData.append('category', document.getElementById('pCategory').value);
    formData.append('price', document.getElementById('pPrice').value);
    formData.append('stock', document.getElementById('pStock').value);
    formData.append('condition', document.getElementById('pCondition').value);
    formData.append('availability', document.getElementById('pAvailability').value);
    formData.append('pickupWindow', document.getElementById('pPickupWindow').value);
    selectedImageFiles.forEach(file => formData.append('images', file));

    let url = '/api/products';
    let method = 'POST';

    if (editingProductId) {
        url = `/api/products/${editingProductId}`;
        method = 'PUT';
    }
    // sellerId is no longer sent from the frontend - the backend reads it
    // from your login token now, so it can't be spoofed.

    const res = await authFetch(url, { method, body: formData });
    const data = await res.json();

    if (!res.ok) {
        const errBox = document.getElementById('productError');
        errBox.textContent = data.message || 'Could not save product.';
        errBox.classList.add('show');
        return;
    }

    closeModal();
    loadProducts();
});

async function deleteProduct(id) {
    if (!confirm('Delete this product?')) return;
    await authFetch(`/api/products/${id}`, { method: 'DELETE' });
    loadProducts();
}

/* ---------------- Orders ---------------- */

async function loadOrders() {
    const res = await authFetch(`/api/orders/seller/${user.id}`);
    const orders = await res.json();

    const body = document.getElementById('ordersBody');
    const empty = document.getElementById('emptyOrders');
    body.innerHTML = '';

    renderSalesSummary(orders);

    if (!orders.length) {
        empty.style.display = 'block';
        return;
    }
    empty.style.display = 'none';

    orders.sort((a, b) => new Date(b.orderDate) - new Date(a.orderDate));

    for (const o of orders) {
        // older orders (placed before this feature) won't have a productName snapshot - fall back to a lookup
        let name = o.productName;
        if (!name) {
            const productRes = await fetch(`/api/products/${o.productId}`);
            name = productRes.ok ? (await productRes.json()).name : 'Product removed';
        }

        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${escapeHtml(name)}</td>
            <td>${o.quantity}</td>
            <td>${o.totalPrice ? o.totalPrice.toFixed(2) : '-'}</td>
            <td>${escapeHtml(o.buyerName || '-')}${o.buyerPhone ? '<br><small class="text-muted">' + escapeHtml(o.buyerPhone) + '</small>' : ''}</td>
            <td>${escapeHtml(o.pickupPoint || 'Bonomaya')}</td>
            <td><span class="status-pill status-${o.status.toLowerCase()}">${o.status}</span></td>
            <td>${new Date(o.orderDate).toLocaleDateString()}</td>
            <td>
                ${o.status === 'PENDING' ? `
                    <button class="btn btn-primary btn-sm" onclick="updateStatus(${o.id}, 'CONFIRMED', this)">Accept order</button>
                    <button class="btn btn-outline btn-sm" style="margin-left:6px;" onclick="cancelOrder(${o.id})">Cancel</button>
                ` : ''}
                ${o.status === 'CONFIRMED' ? `<button class="btn btn-primary btn-sm" onclick="updateStatus(${o.id}, 'READY_FOR_PICKUP', this)">Mark ready for pickup</button>` : ''}
                ${o.status === 'READY_FOR_PICKUP' || o.status === 'COLLECTED' || o.status === 'CANCELLED' ? '<span class="text-muted">No seller action</span>' : ''}
            </td>
        `;
        body.appendChild(row);
    }
}

// Simple sales analytics: total revenue, total orders, top-selling product - all computed
// from the orders this seller already has, no extra API call needed.
function renderSalesSummary(orders) {
    const box = document.getElementById('salesSummary');
    if (!box) return;

    const validOrders = orders.filter(o => o.status !== 'CANCELLED');
    const totalRevenue = validOrders.reduce((sum, o) => sum + (o.totalPrice || 0), 0);
    const totalOrders = validOrders.length;

    const salesByProduct = {};
    validOrders.forEach(o => {
        const key = o.productName || `Product #${o.productId}`;
        salesByProduct[key] = (salesByProduct[key] || 0) + o.quantity;
    });
    const topProduct = Object.entries(salesByProduct).sort((a, b) => b[1] - a[1])[0];

    box.innerHTML = `
        <div class="card" style="padding:16px; flex:1; min-width:150px;">
            <div class="text-muted">Total revenue</div>
            <div style="font-size:1.4rem; font-weight:700;">${totalRevenue.toFixed(2)}</div>
        </div>
        <div class="card" style="padding:16px; flex:1; min-width:150px;">
            <div class="text-muted">Total orders</div>
            <div style="font-size:1.4rem; font-weight:700;">${totalOrders}</div>
        </div>
        <div class="card" style="padding:16px; flex:1; min-width:150px;">
            <div class="text-muted">Top product</div>
            <div style="font-size:1.1rem; font-weight:700;">${topProduct ? `${escapeHtml(topProduct[0])} (${topProduct[1]} sold)` : '-'}</div>
        </div>
    `;
}

async function updateStatus(orderId, status, button) {
    if (button) button.disabled = true;
    try {
        const res = await authFetch(`/api/orders/${orderId}/status`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            alert(data.message || 'Could not update order status.');
            if (button) button.disabled = false;
            return;
        }
        await loadOrders();
    } catch (e) {
        alert('Could not connect to the server. Please try again.');
        if (button) button.disabled = false;
    }
}

async function cancelOrder(orderId) {
    if (!confirm('Cancel this order?')) return;
    const res = await authFetch(`/api/orders/${orderId}/cancel`, { method: 'PUT' });
    if (!res.ok) {
        const data = await res.json();
        alert(data.message || 'Could not cancel order.');
        return;
    }
    loadOrders();
}
