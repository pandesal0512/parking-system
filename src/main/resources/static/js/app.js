/*
 * The signed-in app: shell, menu and routing. The pages themselves live in
 * pages-user.js, pages-staff.js (and profile / password below).
 *
 * HOW TO ADD A PAGE (when a new backend feature is ready):
 *   1. Write a function  Pages.something = function (me, root) { ... }  in a pages file.
 *      It fills the "root" element and may return a cleanup function (for timers).
 *   2. Add one entry to PAGES below: id, label, which roles may see it, and the function.
 * The menu, the #hash routing and the role filtering are automatic.
 *
 * SECURITY NOTE: server text is shown with textContent (via UI.el), never innerHTML.
 * The REAL permission checks are on the server: hiding a menu item is only for convenience.
 */
(function () {
    'use strict';

    if (!Api.getToken()) {
        window.location.replace('index.html');
        return;
    }

    const { el, field, errorBox, clearErrors, showErrors, busy, label } = UI;

    const ALL = ['USER', 'PARKING_MANAGER', 'SUPER_ADMIN'];
    const STAFF = ['PARKING_MANAGER', 'SUPER_ADMIN'];

    // The menu shows pages in this order, filtered by the person's role.
    const PAGES = [
        { id: 'dashboard',      label: 'Dashboard',        roles: STAFF,           render: Pages.dashboard, wide: true },
        { id: 'gate',           label: 'Gate',             roles: STAFF,           render: Pages.gate, wide: true },
        { id: 'vehicle-review', label: 'Vehicles',         roles: STAFF,           render: Pages.vehicleReview, wide: true },
        { id: 'all-reservations', label: 'Reservations',   roles: STAFF,           render: Pages.managerReservations, wide: true },
        { id: 'activity',       label: 'Activity',         roles: STAFF,           render: Pages.activity, wide: true },
        { id: 'capacity',       label: 'Capacity',         roles: ['SUPER_ADMIN'], render: Pages.capacity },
        { id: 'vehicle',        label: 'My vehicle',       roles: ['USER'],        render: Pages.vehicle },
        { id: 'reservations',   label: 'Reservations',     roles: ['USER'],        render: Pages.reservations, wide: true },
        { id: 'logbook',        label: 'Log book',         roles: ['USER'],        render: Pages.logbook, wide: true },
        { id: 'profile',        label: 'My profile',       roles: ALL,             render: renderProfile },
        { id: 'password',       label: 'Change password',  roles: ALL,             render: renderPassword }
    ];

    const nav = document.getElementById('nav');
    const content = document.getElementById('content');
    let me = null;
    let cleanup = null;

    /* ---------- profile ---------- */
    function renderProfile(user, root) {
        const standing = describeStanding(user);
        root.append(
            el('h1', {}, 'My profile'),
            el('dl', { class: 'facts' },
                row('Name', user.fullName),
                row('Username', user.username),
                row('Account type', user.userType ? label('userType', user.userType) : 'Staff account'),
                row('Role', label('role', user.role)),
                user.schoolId ? row('School ID', user.schoolId) : null,
                el('div', {},
                    el('dt', {}, 'Parking standing'),
                    el('dd', { class: 'status' + (standing.bad ? ' bad' : '') }, standing.text)),
                row('Warnings', String(user.warningCount)),
                row('Member since', UI.formatDateTime(user.createdAt))));
    }

    function row(name, value) {
        return el('div', {}, el('dt', {}, name), el('dd', {}, value));
    }

    function describeStanding(user) {
        if (user.registrationBanned) {
            return { bad: true, text: 'Parking registration is disabled. Contact the parking office.' };
        }
        if (user.suspendedUntil && new Date(user.suspendedUntil) > new Date()) {
            return { bad: true, text: 'Reservations suspended until ' + UI.formatDateTime(user.suspendedUntil) };
        }
        return { bad: false, text: 'Good standing' };
    }

    /* ---------- change password ---------- */
    function renderPassword(user, root) {
        const box = errorBox();
        const done = el('div', { class: 'notice', role: 'status', hidden: true });

        const form = el('form', { novalidate: true },
            box,
            field('currentPassword', 'Current password', 'password', { autocomplete: 'current-password' }),
            field('newPassword', 'New password', 'password', { autocomplete: 'new-password', hint: 'At least 8 characters.' }),
            field('confirm', 'Confirm new password', 'password', { autocomplete: 'new-password' }),
            el('button', { type: 'submit', class: 'btn' }, 'Change password'));

        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            clearErrors(form, box);
            done.hidden = true;
            const f = form.elements;

            if (f.newPassword.value !== f.confirm.value) {
                showErrors(form, box, 'Some fields are invalid.', { confirm: 'The passwords do not match.' });
                return;
            }

            await busy(form.querySelector('button[type="submit"]'), async () => {
                try {
                    await Api.post('/api/auth/change-password', {
                        currentPassword: f.currentPassword.value,
                        newPassword: f.newPassword.value
                    });
                    form.reset();
                    const wasForced = me.mustChangePassword;
                    me = await Api.get('/api/users/me');
                    if (wasForced && !me.mustChangePassword) {
                        window.location.hash = '';
                        startApp();
                        return;
                    }
                    done.textContent = 'Password changed.';
                    done.hidden = false;
                } catch (err) {
                    showErrors(form, box, err.message, err.fieldErrors);
                }
            });
        });

        root.append(el('h1', {}, 'Change password'));
        if (user.mustChangePassword) {
            root.append(el('div', { class: 'notice caution', role: 'status' },
                'You are using a temporary password. Choose a new password to continue.'));
        }
        root.append(done, form);
    }

    /* ---------- menu and routing ---------- */
    function visiblePages() {
        // A temporary password must be changed before anything else.
        if (me.mustChangePassword) return PAGES.filter((p) => p.id === 'password');
        return PAGES.filter((p) => p.roles.includes(me.role));
    }

    function buildMenu() {
        nav.replaceChildren();
        if (me.mustChangePassword) return;
        nav.append(el('ul', {}, visiblePages().map((p) =>
            el('li', {}, el('a', { href: '#' + p.id, 'data-page': p.id }, p.label)))));
    }

    function showPage() {
        const pages = visiblePages();
        const wanted = window.location.hash.replace('#', '');
        const page = pages.find((p) => p.id === wanted) || pages[0];

        if (cleanup) { cleanup(); cleanup = null; }

        nav.querySelectorAll('a').forEach((a) => {
            if (a.dataset.page === page.id) a.setAttribute('aria-current', 'page');
            else a.removeAttribute('aria-current');
        });

        content.replaceChildren();
        content.classList.toggle('wide', Boolean(page.wide));
        const result = page.render(me, content);
        if (typeof result === 'function') cleanup = result;
        document.title = page.label + ' - Mabini Colleges Parking';
    }

    function startApp() {
        document.getElementById('who-name').textContent = me.fullName;
        buildMenu();
        showPage();
        content.focus();
    }

    document.getElementById('logout').addEventListener('click', () => {
        Api.clearToken();
        window.location.replace('index.html');
    });

    window.addEventListener('hashchange', () => { showPage(); content.focus(); });

    /* ---------- start ---------- */
    Api.get('/api/users/me')
        .then((user) => { me = user; startApp(); })
        .catch((err) => {
            // A 401 already redirected to the login page inside Api. Anything else: say so.
            content.replaceChildren(
                el('h1', {}, 'Cannot load your account'),
                el('div', { class: 'notice error', role: 'alert' }, err.message),
                el('button', { type: 'button', class: 'btn', onclick: () => window.location.reload() }, 'Try again'));
        });
})();
