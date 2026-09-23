// getCart()/saveCart() come from js/app.js - they're shared with product-details.html
// and checkout.html so every page reads/writes the SAME per-user cart bucket.

function renderCart() {
    const cart = getCart();
    const itemsBox = document.getElementById('cartItems');
    const emptyBox = document.getElementById('cartEmpty');
    const checkoutBox = document.getElementById('cartCheckout');

    if (!cart.length) {
        itemsBox.innerHTML = '';
        emptyBox.style.display = 'block';
        checkoutBox.innerHTML = '';
        return;
    }
    emptyBox.style.display = 'none';

    itemsBox.innerHTML = cart.map((item, idx) => `
        <div class="cart-item">
            ${item.image ? `<img src="${escapeHtml(item.image)}" alt="${escapeHtml(item.name)}">` : '<div style="width:60px;height:60px;background:var(--mint-100);border-radius:6px;"></div>'}
            <div style="flex:1;">
                <strong>${escapeHtml(item.name)}</strong>
                <p class="text-muted">${item.price} each</p>
            </div>
            <div class="qty-controls">
                <button onclick="changeQty(${idx}, -1)">-</button>
                <span>${item.quantity}</span>
                <button onclick="changeQty(${idx}, 1)">+</button>
            </div>
            <strong>${(item.price * item.quantity).toFixed(2)}</strong>
            <button class="btn btn-outline btn-sm" onclick="removeItem(${idx})">Remove</button>
        </div>
    `).join('');

    const total = cart.reduce((sum, item) => sum + item.price * item.quantity, 0);

    const user = getUser();
    checkoutBox.innerHTML = `
        <div class="flex-between" style="margin:16px 0; font-size:1.2rem;">
            <strong>Total</strong>
            <strong>${total.toFixed(2)}</strong>
        </div>
        ${user && user.role !== 'ADMIN' ? `
            <div class="alert alert-success show" style="display:block;">📍 Free collection at Bonomaya. Delivery is not available.</div>
            <button class="btn btn-primary" style="margin-top:10px;" onclick="goToCheckout()">Continue to checkout</button>
        ` : !user ? `
            <a href="login.html" class="btn btn-primary">Log in to checkout</a>
        ` : `<p class="text-muted">Marketplace accounts can check out.</p>`}
    `;
}

function changeQty(idx, delta) {
    const cart = getCart();
    cart[idx].quantity = Math.max(1, Math.min(cart[idx].stock || 99, cart[idx].quantity + delta));
    saveCart(cart);
    renderCart();
}

function removeItem(idx) {
    const cart = getCart();
    cart.splice(idx, 1);
    saveCart(cart);
    renderCart();
}

function goToCheckout() {
    if (!getCart().length) return;
    window.location.href = 'checkout.html?source=cart';
}

renderCart();
