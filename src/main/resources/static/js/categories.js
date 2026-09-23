const CATEGORY_LIST = ['Electronics', 'Fashion', 'Home', 'Books', 'Sports', 'Other'];

let currentPage = 1;
let wishlistIds = [];

document.addEventListener('DOMContentLoaded', () => {
    buildCategoryFilters();
    prefillFromUrl();
    loadWishlistIds().then(loadProducts);
});

function buildCategoryFilters() {
    const wrap = document.getElementById('categoryFilters');
    wrap.innerHTML = `<label class="filter-check"><input type="radio" name="cat" value="" checked> All Categories</label>` +
        CATEGORY_LIST.map(c => `<label class="filter-check"><input type="radio" name="cat" value="${c}"> ${c}</label>`).join('');
}

// If we arrived here from a category tile or a home-page search, honour that.
function prefillFromUrl() {
    const params = new URLSearchParams(window.location.search);
    const category = params.get('category');
    const keyword = params.get('keyword');

    if (keyword) document.getElementById('searchInput').value = keyword;
    if (category) {
        const radio = document.querySelector(`input[name="cat"][value="${CSS.escape(category)}"]`);
        if (radio) radio.checked = true;
        document.getElementById('pageTitle').textContent = category;
        document.getElementById('crumbCurrent').textContent = category;
    }
}

async function loadWishlistIds() {
    const user = getUser();
    if (!user || user.role === 'ADMIN') return;
    try {
        const res = await authFetch('/api/wishlist');
        if (res.ok) wishlistIds = (await res.json()).map(p => p.id);
    } catch (e) { /* not logged in - show empty hearts */ }
}

function selectedConditions() {
    return Array.from(document.querySelectorAll('#conditionFilters input:checked')).map(i => i.value);
}

function buildQuery(page) {
    const params = new URLSearchParams();
    const keyword = document.getElementById('searchInput').value.trim();
    const category = document.querySelector('input[name="cat"]:checked')?.value;
    const minPrice = document.getElementById('minPriceInput').value;
    const maxPrice = document.getElementById('maxPriceInput').value;
    const sort = document.getElementById('sortSelect').value;
    const conditions = selectedConditions();

    if (keyword) params.set('keyword', keyword);
    if (category) params.set('category', category);
    if (minPrice) params.set('minPrice', minPrice);
    if (maxPrice) params.set('maxPrice', maxPrice);
    if (conditions.length === 1) params.set('condition', conditions[0]);
    if (sort) params.set('sort', sort);
    params.set('page', page);
    params.set('size', 12);
    return params.toString();
}

async function loadProducts() {
    const res = await fetch('/api/products?' + buildQuery(currentPage));
    const data = await res.json();

    // When more than one condition checkbox is ticked, the API can only filter
    // by a single value, so we narrow the page's results down client-side.
    const conditions = selectedConditions();
    let products = data.products;
    if (conditions.length > 1) {
        products = products.filter(p => conditions.includes(p.condition));
    }

    renderProducts(products);
    renderPagination(data.currentPage, data.totalPages);
    document.getElementById('resultsCount').textContent = `${data.totalItems} product${data.totalItems === 1 ? '' : 's'} found`;
}

function applyFilters() {
    currentPage = 1;
    loadProducts();
}

function clearFilters() {
    document.getElementById('searchInput').value = '';
    document.querySelector('input[name="cat"][value=""]').checked = true;
    document.getElementById('minPriceInput').value = '';
    document.getElementById('maxPriceInput').value = '';
    document.querySelectorAll('#conditionFilters input').forEach(i => i.checked = false);
    document.getElementById('sortSelect').value = 'newest';
    document.getElementById('pageTitle').textContent = 'All products';
    document.getElementById('crumbCurrent').textContent = 'Categories';
    applyFilters();
}

function goToPage(page) {
    currentPage = page;
    loadProducts();
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

function renderPagination(current, totalPages) {
    const bar = document.getElementById('paginationBar');
    bar.innerHTML = '';
    if (totalPages <= 1) return;
    for (let i = 1; i <= totalPages; i++) {
        const btn = document.createElement('button');
        btn.textContent = i;
        btn.className = 'page-btn' + (i === current ? ' active' : '');
        btn.onclick = () => goToPage(i);
        bar.appendChild(btn);
    }
}

async function toggleWishlist(productId, event) {
    event.preventDefault();
    event.stopPropagation();
    const user = getUser();
    if (!user) { window.location.href = 'login.html'; return; }
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
