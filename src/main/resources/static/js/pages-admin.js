/*
 * Account administration pages:
 *   Pages.staffAccounts  (super admin)  create parking managers, reset passwords, deactivate
 *   Pages.users          (staff)        find users, reset their password, deactivate, clear penalties
 *
 * A temporary password is shown ONCE, right after it is made. The server does not keep it
 * in readable form, so it cannot be shown again (reset it again if it is lost).
 */
(function () {
    'use strict';

    const { el, notice, badge, loading, table, field, errorBox, clearErrors, showErrors, busy, label } = UI;
    const Pages = (window.Pages = window.Pages || {});

    /** The one-time box that shows a temporary password with a copy button. */
    function secretBox(who, password) {
        const copy = el('button', { type: 'button', class: 'btn small quiet' }, 'Copy');
        copy.addEventListener('click', async () => {
            try {
                await navigator.clipboard.writeText(password);
                copy.textContent = 'Copied';
            } catch (e) {
                copy.textContent = 'Select and copy it by hand';
            }
        });
        return el('div', { class: 'notice caution secret-box', role: 'status' },
            el('p', {}, 'Temporary password for ', el('strong', {}, who), '. Give it to them in person. It is shown only once, and they must change it when they sign in.'),
            el('p', { class: 'actions' }, el('code', { class: 'secret' }, password), copy));
    }

    function actionButton(text, cls, handler) {
        const button = el('button', { type: 'button', class: 'btn small ' + cls }, text);
        button.addEventListener('click', () => busy(button, handler));
        return button;
    }

    function accountStatus(u) {
        return u.active ? badge('Active', 'good') : badge('Deactivated', 'bad');
    }

    /* =====================================================================
       Staff accounts (super admin)
       ===================================================================== */
    Pages.staffAccounts = function (me, root) {
        root.append(el('h1', {}, 'Staff accounts'));
        const secretSlot = el('div');
        const listSlot = el('div');

        const box = errorBox();
        const form = el('form', { novalidate: true },
            box,
            el('p', { class: 'soft' }, 'Add a parking manager. The system makes a temporary password for them.'),
            field('username', 'Username', 'text', {
                hint: '4-30 characters: letters, numbers, dot, underscore or dash.',
                attrs: { maxlength: 30, autocomplete: 'off' }
            }),
            field('fullName', 'Full name', 'text', { attrs: { maxlength: 120, autocomplete: 'off' } }),
            el('button', { type: 'submit', class: 'btn' }, 'Create manager account'));

        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            clearErrors(form, box);
            secretSlot.replaceChildren();
            const f = form.elements;
            await busy(form.querySelector('button[type="submit"]'), async () => {
                try {
                    const result = await Api.post('/api/admin/staff', { username: f.username.value, fullName: f.fullName.value });
                    secretSlot.replaceChildren(secretBox(result.user.fullName + ' (' + result.user.username + ')', result.temporaryPassword));
                    form.reset();
                    load();
                } catch (err) {
                    showErrors(form, box, err.message, err.fieldErrors);
                }
            });
        });

        root.append(form, secretSlot, el('h2', { class: 'section' }, 'Current staff'), listSlot);

        async function load() {
            listSlot.replaceChildren(loading());
            try {
                const items = await Api.get('/api/admin/staff');
                const rows = items.map((u) => [
                    el('span', {}, u.fullName, el('br'), el('span', { class: 'soft' }, '@' + u.username)),
                    label('role', u.role),
                    accountStatus(u),
                    u.role === 'PARKING_MANAGER' ? actionsFor(u) : el('span', { class: 'soft' }, u.id === me.id ? 'You' : '')
                ]);
                listSlot.replaceChildren(table(['Name', 'Role', 'Account', ''], rows, 'No staff yet.'));
            } catch (err) {
                listSlot.replaceChildren(notice(err.message, 'error'));
            }
        }

        function actionsFor(u) {
            const reset = actionButton('Reset password', 'quiet', async () => {
                if (!window.confirm('Make a new temporary password for ' + u.fullName + '? Their old password stops working.')) return;
                try {
                    const result = await Api.post('/api/admin/staff/' + u.id + '/reset-password');
                    secretSlot.replaceChildren(secretBox(u.fullName + ' (' + u.username + ')', result.temporaryPassword));
                } catch (err) { window.alert(err.message); }
            });
            const toggle = u.active
                ? actionButton('Deactivate', 'danger', async () => {
                    if (!window.confirm('Deactivate ' + u.fullName + '? They are signed out of the system immediately.')) return;
                    try { await Api.post('/api/admin/staff/' + u.id + '/deactivate'); } catch (err) { window.alert(err.message); }
                    load();
                })
                : actionButton('Activate', '', async () => {
                    try { await Api.post('/api/admin/staff/' + u.id + '/activate'); } catch (err) { window.alert(err.message); }
                    load();
                });
            return el('div', { class: 'actions' }, reset, toggle);
        }

        load();
    };

    /* =====================================================================
       Users (staff)
       ===================================================================== */
    Pages.users = function (me, root) {
        root.append(el('h1', {}, 'Users'));
        // Managers can look people up; resetting passwords, deactivating and clearing penalties is for the super admin.
        const canChange = me.role === 'SUPER_ADMIN';
        if (!canChange) {
            root.append(el('p', { class: 'soft intro' }, 'You can look up users here. Only the super admin can reset passwords, deactivate accounts or clear penalties.'));
        }
        const secretSlot = el('div');
        const search = field('q', 'Search', 'search', {
            required: false, hint: 'Name, username or plate number.', attrs: { maxlength: 60, autocomplete: 'off' }
        });
        const input = search.querySelector('input');
        const slot = el('div');
        root.append(el('div', { class: 'toolbar' }, search), secretSlot, slot);

        async function load() {
            slot.replaceChildren(loading());
            try {
                const items = await Api.get('/api/manager/users?q=' + encodeURIComponent(input.value.trim()));
                const rows = items.map((s) => [
                    el('span', {}, s.user.fullName, el('br'), el('span', { class: 'soft' }, '@' + s.user.username)),
                    label('userType', s.user.userType),
                    s.plate
                        ? el('span', {}, s.plate, el('br'), el('span', { class: 'soft' }, label('vehicleStatus', s.vehicleStatus)))
                        : el('span', { class: 'soft' }, 'No vehicle'),
                    standing(s.user),
                    accountStatus(s.user)
                ].concat(canChange ? [actionsFor(s.user)] : []));
                slot.replaceChildren(table(['Name', 'Type', 'Vehicle', 'Parking standing', 'Account'].concat(canChange ? [''] : []), rows,
                    input.value.trim() ? 'Nobody matches that search.' : 'No users have registered yet.'));
            } catch (err) {
                slot.replaceChildren(notice(err.message, 'error'));
            }
        }

        function standing(u) {
            const parts = [];
            if (u.registrationBanned) parts.push(badge('Registration banned', 'bad'));
            else if (u.suspendedUntil && new Date(u.suspendedUntil) > UI.now()) {
                parts.push(badge('Suspended until ' + UI.formatDateTime(u.suspendedUntil), 'bad'));
            }
            if (u.warningCount > 0) parts.push(badge(u.warningCount + (u.warningCount === 1 ? ' warning' : ' warnings'), 'wait'));
            return parts.length ? el('div', { class: 'actions' }, parts) : el('span', { class: 'soft' }, 'Good standing');
        }

        function actionsFor(u) {
            const buttons = [];
            buttons.push(actionButton('Reset password', 'quiet', async () => {
                if (!window.confirm('Make a new temporary password for ' + u.fullName + '? Their old password stops working.')) return;
                try {
                    const result = await Api.post('/api/manager/users/' + u.id + '/reset-password');
                    secretSlot.replaceChildren(secretBox(u.fullName + ' (' + u.username + ')', result.temporaryPassword));
                } catch (err) { window.alert(err.message); }
            }));
            if (u.warningCount > 0 || u.registrationBanned || (u.suspendedUntil && new Date(u.suspendedUntil) > UI.now())) {
                buttons.push(actionButton('Clear penalties', 'quiet', async () => {
                    if (!window.confirm('Remove all warnings, the suspension and any ban for ' + u.fullName + '?')) return;
                    try { await Api.post('/api/manager/users/' + u.id + '/clear-penalties'); } catch (err) { window.alert(err.message); }
                    load();
                }));
            }
            buttons.push(u.active
                ? actionButton('Deactivate', 'danger', async () => {
                    if (!window.confirm('Deactivate ' + u.fullName + '? They cannot sign in, and their active reservations are cancelled.')) return;
                    try { await Api.post('/api/manager/users/' + u.id + '/deactivate'); } catch (err) { window.alert(err.message); }
                    load();
                })
                : actionButton('Activate', '', async () => {
                    try { await Api.post('/api/manager/users/' + u.id + '/activate'); } catch (err) { window.alert(err.message); }
                    load();
                }));
            return el('div', { class: 'actions' }, buttons);
        }

        let timer = null;
        input.addEventListener('input', () => {
            clearTimeout(timer);
            timer = setTimeout(load, 300);
        });

        load();
        return () => clearTimeout(timer);
    };

    /* =====================================================================
       Demo time (super admin). Only works when the server runs with DEMO_TIME_CONTROL=true.
       ===================================================================== */
    Pages.demo = function (me, root) {
        root.append(el('h1', {}, 'Demo time'));
        const body = el('div', {}, loading());
        root.append(body);
        const resultSlot = el('div', { 'aria-live': 'polite' });

        function show(state) {
            UI.setServerTime(state.now);
            const advanceBox = errorBox();
            const form = el('form', { novalidate: true },
                advanceBox,
                field('minutes', 'Move forward by (minutes)', 'number', { value: '60', attrs: { min: 1, max: 10080, step: 1 } }),
                el('button', { type: 'submit', class: 'btn' }, 'Move clock forward'));

            async function send(path, button, payload) {
                clearErrors(form, advanceBox);
                await busy(button, async () => {
                    try {
                        const result = await Api.post(path, payload);
                        if (result && result.now) {
                            show(result);
                        } else if (result && result.noShows !== undefined) {
                            resultSlot.replaceChildren(notice('Check finished: ' + result.noShows
                                + ' no-show(s) and ' + result.overstays + ' overstay(s) found.'));
                        }
                    } catch (err) {
                        showErrors(form, advanceBox, err.message, err.fieldErrors);
                    }
                });
            }

            form.addEventListener('submit', (event) => {
                event.preventDefault();
                send('/api/admin/demo/time/advance', form.querySelector('button[type="submit"]'),
                    { minutes: Number(form.elements.minutes.value) });
            });

            const quick = [15, 60, 180, 1440].map((m) => {
                const button = el('button', { type: 'button', class: 'btn small quiet' },
                    '+' + (m >= 1440 ? (m / 1440) + ' day' : m >= 60 ? (m / 60) + ' h' : m + ' min'));
                button.addEventListener('click', () => send('/api/admin/demo/time/advance', button, { minutes: m }));
                return button;
            });
            const run = el('button', { type: 'button', class: 'btn' }, 'Run no-show and overstay check now');
            run.addEventListener('click', () => send('/api/admin/demo/run-penalty-check', run));
            const reset = el('button', { type: 'button', class: 'btn quiet' }, 'Back to real time');
            reset.addEventListener('click', () => send('/api/admin/demo/time/reset', reset));

            body.replaceChildren(
                el('div', { class: 'notice caution', role: 'status' },
                    'The system time is ', el('strong', {}, UI.formatDateTime(state.now)),
                    state.offsetMinutes ? ' (moved forward ' + state.offsetMinutes + ' minutes).' : ' (real time).',
                    ' For demos only. Other open pages pick up the new time when you change page or refresh.'),
                form,
                el('p', { class: 'actions' }, quick),
                el('h2', { class: 'section' }, 'Check for penalties'),
                el('p', { class: 'soft' }, 'The system looks for no-shows and overstays by itself every minute. This runs the check right now.'),
                el('p', { class: 'actions' }, run, reset),
                resultSlot);
        }

        Api.get('/api/admin/demo/time').then(show).catch((err) => {
            if (err.status === 404) {
                body.replaceChildren(notice('Demo time control is switched off. To use it, start the backend with DEMO_TIME_CONTROL=true '
                    + '(or app.demo.time-control=true in application-local.properties) and restart. Do not use it outside demos.', 'caution'));
            } else {
                body.replaceChildren(notice(err.message, 'error'));
            }
        });
    };
})();
