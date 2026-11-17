/* Login and register page logic. */
(function () {
    'use strict';

    // Already signed in on this tab? Go straight to the app (it re-checks the token).
    if (Api.getToken()) {
        window.location.replace('app.html');
        return;
    }

    const $ = (id) => document.getElementById(id);

    const loginForm = $('login-form');
    const registerForm = $('register-form');
    const showLogin = $('show-login');
    const showRegister = $('show-register');
    const notice = $('notice');

    function setNotice(text) {
        notice.textContent = text || '';
        notice.hidden = !text;
    }

    function clearErrors(form) {
        form.querySelectorAll('.field-error').forEach((s) => { s.textContent = ''; });
        form.querySelectorAll('[aria-invalid]').forEach((i) => i.removeAttribute('aria-invalid'));
        const box = form.querySelector('.form-error');
        box.textContent = '';
        box.hidden = true;
    }

    function showError(form, err) {
        const box = form.querySelector('.form-error');
        box.textContent = err.message;
        box.hidden = false;

        // Per-field messages from the server's validation
        Object.entries(err.fieldErrors || {}).forEach(([field, message]) => {
            const slot = form.querySelector('[data-error-for="' + field + '"]');
            if (slot) slot.textContent = message;
            const input = form.elements[field];
            if (input) input.setAttribute('aria-invalid', 'true');
        });
    }

    function setBusy(button, busy, busyText) {
        if (busy) {
            button.dataset.label = button.textContent;
            button.textContent = busyText;
        } else if (button.dataset.label) {
            button.textContent = button.dataset.label;
        }
        button.disabled = busy;
    }

    function switchTo(which) {
        const login = which === 'login';
        loginForm.hidden = !login;
        registerForm.hidden = login;
        showLogin.setAttribute('aria-pressed', String(login));
        showRegister.setAttribute('aria-pressed', String(!login));
        clearErrors(loginForm);
        clearErrors(registerForm);
    }

    showLogin.addEventListener('click', () => { setNotice(''); switchTo('login'); $('login-username').focus(); });
    showRegister.addEventListener('click', () => { setNotice(''); switchTo('register'); $('reg-fullName').focus(); });

    if (new URLSearchParams(window.location.search).get('expired')) {
        setNotice('Your session ended. Sign in again to continue.');
    }

    loginForm.addEventListener('submit', async (event) => {
        event.preventDefault();
        clearErrors(loginForm);
        const button = loginForm.querySelector('button[type="submit"]');
        setBusy(button, true, 'Signing in...');
        try {
            const data = await Api.post('/api/auth/login', {
                username: loginForm.elements.username.value,
                password: loginForm.elements.password.value
            });
            Api.setToken(data.token);
            window.location.href = 'app.html';
        } catch (err) {
            showError(loginForm, err);
            loginForm.elements.password.value = '';
            setBusy(button, false);
        }
    });

    registerForm.addEventListener('submit', async (event) => {
        event.preventDefault();
        clearErrors(registerForm);
        const f = registerForm.elements;

        if (f.password.value !== f.confirm.value) {
            showError(registerForm, {
                message: 'Some fields are invalid.',
                fieldErrors: { confirm: 'The passwords do not match.' }
            });
            return;
        }

        const button = registerForm.querySelector('button[type="submit"]');
        setBusy(button, true, 'Creating account...');
        try {
            await Api.post('/api/auth/register', {
                username: f.username.value,
                fullName: f.fullName.value,
                password: f.password.value,
                userType: f.userType.value || null,
                schoolId: f.schoolId.value
            });
            const username = f.username.value.trim();
            registerForm.reset();
            switchTo('login');
            loginForm.elements.username.value = username;
            setNotice('Account created. Sign in to continue.');
            loginForm.elements.password.focus();
        } catch (err) {
            showError(registerForm, err);
        } finally {
            setBusy(button, false);
        }
    });
})();
