
function showError(message) {
    const box = document.getElementById('errorBox');
    if (!box) return;
    box.textContent = message;
    box.classList.add('show');
}

function hideError() {
    const box = document.getElementById('errorBox');
    if (box) box.classList.remove('show');
}

function showSuccess(message) {
    const box = document.getElementById('successBox');
    if (!box) return;
    box.textContent = message;
    box.classList.add('show');
}

// ---------- Login form ----------
const loginForm = document.getElementById('loginForm');
if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideError();

        const email = document.getElementById('email').value;
        const password = document.getElementById('password').value;

        const res = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });

        const data = await res.json();

        if (!res.ok) {
            showError(data.message || 'Login failed');
            return;
        }

        saveUser(data);
        mergeGuestCartIntoUser();

        if (data.role === 'ADMIN') window.location.href = 'admin-dashboard.html';
        else window.location.href = 'index.html';
    });
}

// ---------- Register form ----------
const registerForm = document.getElementById('registerForm');
if (registerForm) {
    registerForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideError();

        const name = document.getElementById('name').value;
        const email = document.getElementById('email').value;
        const password = document.getElementById('password').value;

        const res = await fetch('/api/auth/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, email, password })
        });

        const data = await res.json();

        if (!res.ok) {
            showError(data.message || 'Registration failed');
            return;
        }

        showSuccess('Account created! Redirecting to login...');
        registerForm.reset();
        setTimeout(() => { window.location.href = 'login.html'; }, 1200);
    });
}

// ---------- Forgot password form ----------
const forgotForm = document.getElementById('forgotForm');
if (forgotForm) {
    forgotForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideError();

        const email = document.getElementById('email').value;

        const res = await fetch('/api/auth/forgot-password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email })
        });

        const data = await res.json();

        if (!res.ok) {
            showError(data.message || 'Something went wrong');
            return;
        }

        showSuccess(data.message);
        // devResetLink only exists because this project has no email server configured -
        // see AuthController.forgotPassword(). Shown here so the flow is testable locally.
        const devBox = document.getElementById('devResetLink');
        if (devBox && data.devResetLink) {
            devBox.innerHTML = `Dev/testing shortcut (no email server set up): <a href="${data.devResetLink}">${data.devResetLink}</a>`;
            devBox.style.display = 'block';
        }
        forgotForm.reset();
    });
}

// ---------- Reset password form ----------
const resetForm = document.getElementById('resetForm');
if (resetForm) {
    const params = new URLSearchParams(window.location.search);
    const token = params.get('token');
    if (!token) {
        showError('This reset link is missing its token. Please request a new one.');
        resetForm.style.display = 'none';
    }

    resetForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideError();

        const newPassword = document.getElementById('newPassword').value;
        const confirmPassword = document.getElementById('confirmPassword').value;
        if (newPassword !== confirmPassword) {
            showError('Passwords do not match');
            return;
        }

        const res = await fetch('/api/auth/reset-password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ token, newPassword })
        });

        const data = await res.json();

        if (!res.ok) {
            showError(data.message || 'Could not reset password');
            return;
        }

        showSuccess(data.message || 'Password reset successful.');
        resetForm.reset();
        setTimeout(() => { window.location.href = 'login.html'; }, 1500);
    });
}
