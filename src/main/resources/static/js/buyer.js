const user = requireMarketplaceUser();
const productCache = {};

document.addEventListener('DOMContentLoaded', () => loadOrders());

async function getProductName(productId) {
    if (productCache[productId]) return productCache[productId];
    const res = await fetch(`/api/products/${productId}`);
    if (!res.ok) return 'Product removed';
    const p = await res.json();
    productCache[productId] = p.name;
    return p.name;
}

async function loadOrders() {
    const res = await authFetch(`/api/orders/buyer/${user.id}`);
    const orders = await res.json().catch(() => []);
    const body = document.getElementById('ordersBody');
    const empty = document.getElementById('emptyOrders');
    if (!body || !empty) return;
    body.innerHTML = '';
    if (!res.ok || !orders.length) { empty.style.display = 'block'; return; }
    empty.style.display = 'none';
    orders.sort((a, b) => new Date(b.orderDate) - new Date(a.orderDate));
    for (const o of orders) {
        const name = o.productName || await getProductName(o.productId);
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${escapeHtml(name)}</td><td>${o.quantity}</td><td>${o.totalPrice ? Number(o.totalPrice).toFixed(2) : '-'}</td>
            <td>${escapeHtml(o.pickupPoint || 'Bonomaya')}</td><td><span class="status-pill status-${String(o.status).toLowerCase()}">${escapeHtml(o.status)}</span></td>
            <td>${new Date(o.orderDate).toLocaleDateString()}</td><td>${o.status === 'CANCELLED' ? '-' : escapeHtml(o.pickupCode || '-')}</td>
            <td>${o.status === 'PENDING' ? `<button class="btn btn-outline btn-sm" onclick="cancelOrder(${o.id})">Cancel</button>` : ''}
                ${o.status === 'READY_FOR_PICKUP' ? `<button class="btn btn-primary btn-sm" onclick="collectOrder(${o.id})">Confirm collection</button>` : ''}
                ${o.status === 'COLLECTED' ? `<a href="review.html?orderId=${o.id}" class="btn btn-primary btn-sm">Rate & Review</a>` : ''}</td>`;
        body.appendChild(row);
    }
}

async function collectOrder(orderId) {
    const pickupCode = prompt('Enter the 6-character pickup code shown with this order:');
    if (!pickupCode) return;
    const res = await authFetch(`/api/orders/${orderId}/collect`, {method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({pickupCode})});
    const data = await res.json().catch(()=>({}));
    if (!res.ok) return alert(data.message || 'Could not confirm collection.');
    loadOrders();
}
async function cancelOrder(orderId) {
    if (!confirm('Cancel this order?')) return;
    const res = await authFetch(`/api/orders/${orderId}/cancel`, {method:'PUT'});
    if (!res.ok) { const data=await res.json().catch(()=>({})); alert(data.message || 'Could not cancel order.'); return; }
    loadOrders();
}
// escapeHtml() lives in js/app.js and is shared across pages.
