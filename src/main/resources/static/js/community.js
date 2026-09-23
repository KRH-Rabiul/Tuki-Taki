// Shared script for community.html (list) and community-post.html (detail).
// Each function below checks for the DOM elements it needs, so loading this
// one file on either page just works - only the relevant half runs.

let allPosts = [];

document.addEventListener('DOMContentLoaded', () => {
    if (document.getElementById('postList')) initListPage();
    if (document.getElementById('postDetail')) initDetailPage();
});

function timeAgo(dateStr) {
    const diffMs = Date.now() - new Date(dateStr).getTime();
    const mins = Math.floor(diffMs / 60000);
    if (mins < 1) return 'just now';
    if (mins < 60) return `${mins}m ago`;
    const hours = Math.floor(mins / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    if (days < 7) return `${days}d ago`;
    return new Date(dateStr).toLocaleDateString();
}

function initialOf(name) {
    return (name || '?').trim().charAt(0).toUpperCase() || '?';
}

// One shared avatar renderer for the Community board (post list, post detail,
// replies) so a member's real profile photo shows everywhere their name does,
// exactly like it already does in Messages - falling back to their initial
// only when they have no photo set.
function communityAvatarHTML(name, photoUrl, extraStyle) {
    const style = extraStyle ? ` style="${extraStyle}"` : '';
    return photoUrl
        ? `<span class="avatar has-photo"${style}><img src="${escapeHtml(photoUrl)}" alt=""></span>`
        : `<span class="avatar"${style}>${escapeHtml(initialOf(name))}</span>`;
}

// ==================== LIST PAGE ====================

function initListPage() {
    loadPosts();
    document.getElementById('communitySearch').addEventListener('input', debounceCommunity(renderPostList, 250));
    document.getElementById('postForm').addEventListener('submit', submitNewPost);
}

let communitySearchTimer;
function debounceCommunity(fn, delay) {
    return (...args) => { clearTimeout(communitySearchTimer); communitySearchTimer = setTimeout(() => fn(...args), delay); };
}

async function loadPosts() {
    try {
        const res = await fetch('/api/community/posts');
        allPosts = res.ok ? await res.json() : [];
    } catch (e) {
        allPosts = [];
    }
    renderPostList();
}

function renderPostList() {
    const q = (document.getElementById('communitySearch').value || '').trim().toLowerCase();
    const filtered = q
        ? allPosts.filter(p => `${p.title} ${p.body || ''} ${p.authorName || ''}`.toLowerCase().includes(q))
        : allPosts;

    document.getElementById('discussionCount').textContent = `${filtered.length} active discussion${filtered.length === 1 ? '' : 's'}`;

    const list = document.getElementById('postList');
    const empty = document.getElementById('emptyState');

    if (filtered.length === 0) {
        list.innerHTML = '';
        empty.style.display = 'block';
        return;
    }
    empty.style.display = 'none';

    list.innerHTML = filtered.map((p, i) => `
        <a class="community-post-card" href="community-post.html?id=${p.id}" style="animation-delay:${Math.min(i, 8) * 30}ms; text-decoration:none; color:inherit;">
            <div class="community-post-top">
                ${communityAvatarHTML(p.authorName, p.authorProfileImageUrl)}
                <div class="community-post-meta">
                    <strong>${escapeHtml(p.authorName || 'Unknown')}${verifiedBadgeHTML(p.authorVerified)}</strong>
                    <small>${timeAgo(p.createdAt)}</small>
                </div>
            </div>
            <h3>${escapeHtml(p.title)}</h3>
            ${p.body ? `<p>${escapeHtml(p.body.slice(0, 140))}${p.body.length > 140 ? '…' : ''}</p>` : ''}
            <div class="community-post-foot">
                <div class="community-post-stats">
                    <span>👁 ${p.viewCount} view${p.viewCount === 1 ? '' : 's'}</span>
                    <span>💬 ${p.replyCount} repl${p.replyCount === 1 ? 'y' : 'ies'}</span>
                </div>
                <span class="text-button" style="padding:0;">Join discussion →</span>
            </div>
        </a>
    `).join('');
}

function openPostModal() {
    if (!getUser()) {
        if (confirm('You need to log in to post on the Community. Go to the login page now?')) {
            location.href = 'login.html';
        }
        return;
    }
    document.getElementById('postError').style.display = 'none';
    document.getElementById('postForm').reset();
    document.getElementById('postModal').classList.add('show');
}

function closePostModal() {
    document.getElementById('postModal').classList.remove('show');
}

async function submitNewPost(e) {
    e.preventDefault();
    const title = document.getElementById('postTitle').value.trim();
    const body = document.getElementById('postBody').value.trim();
    const errorBox = document.getElementById('postError');
    const btn = document.getElementById('postSubmitBtn');

    errorBox.style.display = 'none';
    btn.disabled = true;
    btn.textContent = 'Posting…';

    try {
        const res = await authFetch('/api/community/posts', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title, body })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            errorBox.textContent = data.message || 'Could not create post.';
            errorBox.style.display = 'block';
            return;
        }
        closePostModal();
        await loadPosts();
    } finally {
        btn.disabled = false;
        btn.textContent = 'Post to Community';
    }
}

// ==================== DETAIL PAGE ====================

function initDetailPage() {
    const postId = new URLSearchParams(location.search).get('id');
    if (!postId) {
        document.getElementById('postDetail').innerHTML = '<p class="text-muted">Discussion not found.</p>';
        return;
    }
    loadPostDetail(postId);

    const replyForm = document.getElementById('replyForm');
    if (replyForm) replyForm.addEventListener('submit', e => submitReply(e, postId));
}

async function loadPostDetail(postId) {
    const detailBox = document.getElementById('postDetail');
    const res = await fetch(`/api/community/posts/${postId}`);
    if (!res.ok) {
        detailBox.innerHTML = '<p class="text-muted">This discussion could not be found - it may have been removed.</p>';
        return;
    }
    const data = await res.json();
    const post = data.post;
    const replies = data.replies || [];
    const me = getUser();

    const canDeletePost = me && (String(me.id) === String(post.authorId) || me.role === 'ADMIN');
    const canMessageAuthor = me && me.role !== 'ADMIN' && String(me.id) !== String(post.authorId);

    detailBox.innerHTML = `
        <div class="community-post-top">
            ${communityAvatarHTML(post.authorName, post.authorProfileImageUrl)}
            <div class="community-post-meta">
                <strong>${escapeHtml(post.authorName || 'Unknown')}${verifiedBadgeHTML(post.authorVerified)}</strong>
                <small>${new Date(post.createdAt).toLocaleDateString()}</small>
            </div>
        </div>
        <h1>${escapeHtml(post.title)}</h1>
        ${post.body ? `<p>${escapeHtml(post.body)}</p>` : ''}
        <div class="community-post-foot">
            <div class="community-post-stats">
                <span>👁 ${post.viewCount} view${post.viewCount === 1 ? '' : 's'}</span>
                <span>💬 ${post.replyCount} repl${post.replyCount === 1 ? 'y' : 'ies'}</span>
            </div>
            <div class="admin-actions-cell">
                ${canMessageAuthor ? `<button class="btn btn-primary btn-sm" onclick="messageAuthor(${post.authorId})">💬 Message author</button>` : ''}
                ${canDeletePost ? `<button class="btn btn-danger btn-sm" onclick="deletePost(${post.id})">Delete post</button>` : ''}
            </div>
        </div>
    `;

    document.getElementById('replyHeading').textContent = `${replies.length} Campus Repl${replies.length === 1 ? 'y' : 'ies'}`;

    const replyList = document.getElementById('replyList');
    replyList.innerHTML = replies.map(r => {
        const canDeleteReply = me && (String(me.id) === String(r.authorId) || me.role === 'ADMIN');
        return `
        <div class="community-reply">
            <div class="community-reply-head">
                ${communityAvatarHTML(r.authorName, r.authorProfileImageUrl, 'width:26px;height:26px;font-size:.72rem;')}
                <strong>${escapeHtml(r.authorName || 'Unknown')}${verifiedBadgeHTML(r.authorVerified)}</strong>
                <small>${timeAgo(r.createdAt)}</small>
                ${canDeleteReply ? `<button class="text-button" style="margin-left:auto;padding:0;" onclick="deleteReply(${r.id}, '${postId}')">Delete</button>` : ''}
            </div>
            <div>${escapeHtml(r.body)}</div>
        </div>`;
    }).join('');

    document.getElementById('replyFormWrap').style.display = me ? 'block' : 'none';
    document.getElementById('loginToReply').style.display = me ? 'none' : 'block';
}

async function submitReply(e, postId) {
    e.preventDefault();
    const textarea = document.getElementById('replyBody');
    const errorBox = document.getElementById('replyError');
    const body = textarea.value.trim();
    if (!body) return;

    errorBox.style.display = 'none';
    const res = await authFetch(`/api/community/posts/${postId}/replies`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ body })
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
        errorBox.textContent = data.message || 'Could not post reply.';
        errorBox.style.display = 'block';
        return;
    }
    textarea.value = '';
    await loadPostDetail(postId);
}

async function deletePost(id) {
    if (!confirm('Delete this discussion and all its replies?')) return;
    const res = await authFetch(`/api/community/posts/${id}`, { method: 'DELETE' });
    if (!res.ok) { alert('Could not delete this post.'); return; }
    location.href = 'community.html';
}

async function deleteReply(id, postId) {
    if (!confirm('Delete this reply?')) return;
    const res = await authFetch(`/api/community/replies/${id}`, { method: 'DELETE' });
    if (!res.ok) { alert('Could not delete this reply.'); return; }
    loadPostDetail(postId);
}

// Starts (or reopens) a general conversation with the post's author, not tied
// to any specific product - see ConversationController#start for how a null
// productId is handled on the backend.
async function messageAuthor(authorId) {
    const res = await authFetch('/api/messages/start', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sellerId: authorId })
    });
    if (!res.ok) {
        const d = await res.json().catch(() => ({}));
        alert(d.message || 'Could not start a conversation.');
        return;
    }
    const conversation = await res.json();
    location.href = `messages.html?conversation=${conversation.id}`;
}
