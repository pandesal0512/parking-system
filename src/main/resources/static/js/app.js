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
    /** A page made of tabs, e.g. Vehicles has "Review" and "Capacity". Each tab is an ordinary page function. */
    function tabbed(title, tabs) {
        return function (user, root) {
            const available = tabs.filter((t) => !t.roles || t.roles.includes(user.role));
            root.append(el('h1', {}, title));
            const bar = el('div', { class: 'tabs', role: 'tablist', 'aria-label': title });
            const panel = el('div', { class: 'tab-panel', role: 'tabpanel' });
            if (available.length > 1) root.append(bar);
            root.append(panel);
            let cleanup = null;

            function open(index) {
                if (typeof cleanup === 'function') cleanup();
                cleanup = null;
                panel.replaceChildren();
                bar.querySelectorAll('button').forEach((b, j) => b.setAttribute('aria-selected', String(j === index)));
                cleanup = available[index].render(user, panel);
                const heading = panel.querySelector(':scope > h1'); // the page title is already shown above the tabs
                if (heading) heading.remove();
            }
            available.forEach((t, i) => bar.append(el('button', {
                type: 'button', role: 'tab', class: 'tab', 'aria-selected': 'false', onclick: () => open(i)
            }, t.label)));
            open(0);
            return () => { if (typeof cleanup === 'function') cleanup(); };
        };
    }

    const PAGES = [
        { id: 'dashboard',      label: 'Dashboard',        roles: STAFF,           render: Pages.dashboard, wide: true },
        { id: 'gate',           label: 'Gate',             roles: ['PARKING_MANAGER'],           render: Pages.gate, wide: true },
        { id: 'vehicle-review', label: 'Vehicles',         roles: STAFF,           render: tabbed('Vehicles', [
            { label: 'Vehicles', render: Pages.vehicleReview },
            { label: 'Reservations', render: Pages.managerReservations },
            { label: 'Capacity', render: Pages.capacity, roles: ['SUPER_ADMIN'] }
        ]), wide: true },
        { id: 'activity',       label: 'Activity',         roles: STAFF,           render: Pages.activity, wide: true },
        { id: 'users',          label: 'Users',            roles: STAFF,           render: tabbed('Users', [
            { label: 'Accounts', render: Pages.users },
            { label: 'Penalties', render: Pages.penalties },
            { label: 'Staff accounts', render: Pages.staffAccounts, roles: ['SUPER_ADMIN'] }
        ]), wide: true },
        { id: 'demo',           label: 'Demo time',        roles: ['SUPER_ADMIN'], render: Pages.demo },
        { id: 'vehicle',        label: 'My vehicle',       roles: ['USER'],        render: Pages.vehicle },
        { id: 'reservations',   label: 'Reservations',        roles: ['USER'],        render: Pages.reservations, wide: true },
        { id: 'logbook',        label: 'Log book',         roles: ['USER'],        render: Pages.logbook, wide: true },
        { id: 'my-penalties',   label: 'Warnings',         roles: ['USER'], userTypes: ['STUDENT'],        render: Pages.myPenalties, wide: true },
        // Settings is opened from the account menu, not listed in the sidebar.
        { id: 'settings',       label: 'Settings',         roles: ALL,             render: renderSettings, inMenu: false }
    ];

    const nav = document.getElementById('nav');
    const content = document.getElementById('content');
    let me = null;
    let cleanup = null;

    /* ---------- settings: appearance, profile, password ---------- */
    function renderSettings(user, root) {
        root.append(el('h1', {}, 'Settings'));
        if (user.mustChangePassword) {
            root.append(el('div', { class: 'notice caution', role: 'status' },
                'You are using a temporary password. Choose a new password to continue.'));
        } else {
            root.append(appearanceSection());
        }
        if (!user.mustChangePassword) root.append(profileSection(user));
        root.append(passwordSection(user));
    }

    function appearanceSection() {
        const current = Shared.theme.get();
        const options = [
            { value: 'light', text: 'Light' },
            { value: 'dark', text: 'Dark' },
            { value: 'system', text: 'System' }
        ];
        const group = el('fieldset', { class: 'theme-choices' },
            el('legend', {}, 'Choose how Mabini Colleges Parking looks on this device.'),
            options.map((o) => {
                const input = el('input', { type: 'radio', name: 'theme', value: o.value, checked: o.value === current });
                input.addEventListener('change', () => Shared.theme.set(o.value));
                return el('label', { class: 'theme-choice theme-' + o.value },
                    input,
                    el('span', { class: 'theme-preview', 'aria-hidden': 'true' },
                        el('i', { class: 'p-side' }), el('i', { class: 'p-main' }, el('b'), el('b'))),
                    el('span', { class: 'theme-name' }, o.text));
            }));
        return el('section', { id: 'appearance' }, el('h2', { class: 'section first' }, 'Appearance'), group);
    }

    function profileSection(user) {
        const standing = describeStanding(user);
        return el('section', { id: 'profile' },
            el('h2', { class: 'section' }, 'Profile'),
            el('dl', { class: 'facts' },
                row('Name', user.fullName),
                row('Username', user.username),
                row('Account type', user.userType ? label('userType', user.userType) : 'Staff account'),
                row('Role', label('role', user.role)),
                user.schoolId ? row('School ID', user.schoolId) : null,
                // Parking standing and warnings apply to students only (faculty, personnel and staff are never penalised).
                user.role === 'USER' && user.userType === 'STUDENT' ? el('div', {},
                    el('dt', {}, 'Parking standing'),
                    el('dd', { class: 'status' + (standing.bad ? ' bad' : '') }, standing.text)) : null,
                user.role === 'USER' && user.userType === 'STUDENT' ? row('Warnings', String(user.warningCount)) : null,
                row('Member since', UI.formatDateTime(user.createdAt))));
    }

    function row(name, value) {
        return el('div', {}, el('dt', {}, name), el('dd', {}, value));
    }

    function describeStanding(user) {
        if (user.registrationBanned) {
            return { bad: true, text: 'Parking registration is disabled. Contact the parking office.' };
        }
        if (user.suspendedUntil && new Date(user.suspendedUntil) > UI.now()) {
            return { bad: true, text: 'Reservations suspended until ' + UI.formatDateTime(user.suspendedUntil) };
        }
        return { bad: false, text: 'Good standing' };
    }

    function passwordSection(user) {
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

        return el('section', { id: 'password' }, el('h2', { class: 'section' }, 'Password'), done, form);
    }

    /* ---------- menu and routing ---------- */
    function visiblePages() {
        // A temporary password must be changed before anything else.
        if (me.mustChangePassword) return PAGES.filter((p) => p.id === 'settings');
        return PAGES.filter((p) => p.roles.includes(me.role) && (!p.userTypes || p.userTypes.includes(me.userType)));
    }

    function buildMenu() {
        nav.replaceChildren();
        if (me.mustChangePassword) return;
        nav.append(el('ul', {}, visiblePages().filter((p) => p.inMenu !== false).map((p) =>
            el('li', {}, el('a', { href: '#' + p.id, 'data-page': p.id }, el('span', {}, p.label))))));
    }


    /** Ask the server what time it is, so rules about "now" follow the server (and the demo clock). */
    async function syncTime() {
        if (me.mustChangePassword) return;
        try {
            const t = await Api.get('/api/time');
            UI.setServerTime(t.now);
        } catch (e) { /* not critical: keep the last known offset */ }
    }

    let renderCount = 0;
    async function showPage() {
        const count = ++renderCount;
        await syncTime();
        if (count !== renderCount) return; // a newer navigation took over
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
        closeMenus();
    }

    /* ---------- account menu and mobile drawer ---------- */
    const sidebar = document.getElementById('sidebar');
    const userButton = document.getElementById('user-button');
    const userMenu = document.getElementById('user-menu');
    const menuToggle = document.getElementById('menu-toggle');

    function setUserMenu(open) {
        userMenu.hidden = !open;
        userButton.setAttribute('aria-expanded', String(open));
    }
    function closeMenus() {
        setUserMenu(false);
        sidebar.classList.remove('open');
        menuToggle.setAttribute('aria-expanded', 'false');
    }
    userButton.addEventListener('click', () => setUserMenu(userMenu.hidden));
    menuToggle.addEventListener('click', () => {
        const open = !sidebar.classList.contains('open');
        sidebar.classList.toggle('open', open);
        menuToggle.setAttribute('aria-expanded', String(open));
    });
    document.addEventListener('click', (event) => {
        if (!userMenu.hidden && !event.target.closest('.user')) setUserMenu(false);
    });
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && !userMenu.hidden) { setUserMenu(false); userButton.focus(); }
    });
    document.getElementById('menu-settings').addEventListener('click', () => setUserMenu(false));

    function startApp() {
        document.getElementById('who-name').textContent = me.fullName;
        document.getElementById('who-role').textContent = label('role', me.role);
        document.getElementById('menu-username').textContent = '@' + me.username;
        document.getElementById('avatar').textContent = (me.fullName || me.username || '?').trim().charAt(0).toUpperCase();
        document.getElementById('menu-settings').hidden = me.mustChangePassword;
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
