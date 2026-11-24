/*
 * Pages for the parking manager and super admin:
 * dashboard, gate simulator, vehicle review, reservations, activity, capacity.
 *
 * A page function may RETURN a cleanup function. app.js calls it when you leave the page
 * (used to stop the dashboard's auto-refresh timer).
 */
(function () {
    'use strict';

    const { el, notice, badge, loading, table, field, errorBox, clearErrors, showErrors, busy, label } = UI;
    const Pages = (window.Pages = window.Pages || {});

    const VEHICLE_TONE = { APPROVED: 'good', PENDING: 'wait', REJECTED: 'bad', REVOKED: 'bad' };
    const RESERVATION_TONE = { PENDING: 'wait', ACTIVE: 'good', REJECTED: 'bad', EXPIRED: '', COMPLETED: '', CANCELLED: '', NO_SHOW: 'bad' };

    function ownerCell(name, username) {
        return el('span', {}, name, el('br'), el('span', { class: 'soft' }, '@' + username));
    }

    /* Badges (Overstay, Manual override) stay visible. Written notes can be long, so they open in a pop-up. */
    function noteCell(l) {
        const badges = [];
        if (l.overstay) badges.push(badge('Overstay', 'bad'));
        if (l.override) badges.push(badge('Manual override', 'wait'));
        const notes = [];
        if (l.visitReason) notes.push(['Reason for the visit', l.visitReason]);
        if (l.override && l.overrideReason) notes.push(['Override reason', l.overrideReason]);
        if (!badges.length && !notes.length) return '';
        const parts = [...badges];
        if (notes.length) {
            parts.push(el('button', { type: 'button', class: 'btn small quiet', onclick: () => openNotes(l, notes) }, 'Notes'));
        }
        return el('div', { class: 'actions' }, parts);
    }

    function openNotes(l, notes) {
        const close = el('button', { type: 'button', class: 'btn' }, 'Close');
        const box = el('dialog', { class: 'notes-dialog', 'aria-label': 'Notes' },
            el('h2', {}, 'Notes'),
            el('p', { class: 'soft' }, [l.identifier || 'No plate', l.name].filter(Boolean).join(' - ')),
            ...notes.map(([title, text]) => el('div', { class: 'notes-item' }, el('strong', {}, title), el('p', {}, text))),
            el('div', { class: 'notes-actions' }, close));
        const done = () => { box.close(); box.remove(); };
        close.addEventListener('click', done);
        box.addEventListener('click', (e) => { if (e.target === box) done(); });   // click outside closes it
        box.addEventListener('cancel', () => box.remove());                       // Esc closes it
        document.body.append(box);
        box.showModal();
        close.focus();
    }

    function logRows(items) {
        return items.map((l) => [
            UI.when(l.entryTime),
            l.exitTime ? UI.when(l.exitTime) : badge('Inside now', 'wait'),
            l.identifier || el('span', { class: 'soft' }, 'No plate'),
            el('span', {}, l.name || '', l.visitor ? [' ', badge('Visitor')] : ''),
            label('vehicleType', l.vehicleType),
            noteCell(l)
        ]);
    }
    const LOG_HEADERS = ['Entered', 'Left', 'Plate', 'Name', 'Type', 'Note'];

    function deniedRows(items) {
        return items.map((d) => [
            UI.when(d.attemptedAt),
            d.identifier || el('span', { class: 'soft' }, 'No plate'),
            label('deny', d.reason)
        ]);
    }
    const DENIED_HEADERS = ['When', 'Plate', 'Reason'];

    /* =====================================================================
       Dashboard (refreshes by itself every 10 seconds)
       ===================================================================== */
    Pages.dashboard = function (me, root) {
        root.append(el('h1', {}, 'Dashboard'));
        const body = el('div', {}, loading());
        root.append(body);

        const ring = UI.ring;

        function render(d) {
            const bays = d.types.map((t) => {
                const name = (t.area === 'FACULTY' ? label('area', t.area) + ': ' : '') + label('vehicleType', t.vehicleType);
                return el('div', { class: 'bay-card' },
                    ring(t.inside, t.capacity, name),
                    el('strong', { class: 'bay-name' }, name),
                    el('p', { class: 'soft' }, t.available + ' free. ' + t.reservedNow + ' reserved right now.'));
            });

            const pendingLine = d.pendingVehicles > 0
                ? el('span', { class: 'drill-head' }, String(d.pendingVehicles), ' ', el('a', { href: '#vehicle-review', class: 'btn small quiet' }, 'Review them'))
                : '0';

            body.replaceChildren(
                el('h2', { class: 'section first' }, 'Spaces in use right now'),
                el('div', { class: 'bay-grid' }, ...bays),
                el('h2', { class: 'section' }, 'Today'),
                el('dl', { class: 'facts' },
                    drillFact('reservations', 'Reservations starting today', d.reservationsToday),
                    drillFact('entered', 'Vehicles that entered', d.entriesToday),
                    drillFact('overstay', 'Vehicles inside past their reservation', d.overstayingNow),
                    drillFact('refused', 'Entries refused', d.deniedToday),
                    fact('Vehicles waiting for approval', pendingLine)),
                el('p', { class: 'soft updated' }, 'Updated ' + UI.formatTime(UI.now()) + '. This page refreshes every 10 seconds.'));
        }

        function fact(name, value) {
            return el('div', {}, el('dt', {}, name), el('dd', {}, value));
        }

        // A number with a Show / Hide button that lists the vehicles behind it, right under the number.
        // `openKey` survives the 10-second refresh, so an open list stays open.
        let openKey = null;
        function drillFact(key, name, count) {
            const isOpen = openKey === key;
            const panel = el('div', { class: 'drill', hidden: !isOpen });
            const button = el('button', { type: 'button', class: 'btn small quiet', 'aria-expanded': String(isOpen) },
                isOpen ? 'Hide' : 'Show who');
            if (count === 0) button.hidden = true;
            button.addEventListener('click', () => {
                if (openKey === key) { openKey = null; panel.hidden = true; button.textContent = 'Show who'; button.setAttribute('aria-expanded', 'false'); return; }
                openKey = key;
                refresh();
            });
            if (isOpen) fillPanel(key, panel);
            return el('div', {}, el('dt', {}, name),
                el('dd', {}, el('span', { class: 'drill-head' }, String(count), ' ', button), panel));
        }

        async function fillPanel(key, panel) {
            panel.replaceChildren(loading());
            const startOfToday = new Date(UI.now()); startOfToday.setHours(0, 0, 0, 0);
            const isToday = (iso) => new Date(iso) >= startOfToday;
            try {
                if (key === 'entered' || key === 'overstay') {
                    const logs = await Api.get('/api/manager/logs?limit=200');
                    const shown = key === 'entered'
                        ? logs.filter((l) => isToday(l.entryTime))
                        : logs.filter((l) => !l.exitTime && l.overstay);
                    panel.replaceChildren(table(LOG_HEADERS, logRows(shown), 'Nobody to show.'));
                } else if (key === 'refused') {
                    const items = await Api.get('/api/manager/denied?limit=200');
                    panel.replaceChildren(table(DENIED_HEADERS, deniedRows(items.filter((d) => isToday(d.attemptedAt))), 'Nothing to show.'));
                } else {
                    const items = await Api.get('/api/manager/reservations?date=' + UI.toDateInputValue(UI.now()) + '&status=ACTIVE');
                    const rows = items.map((r) => [UI.formatRange(r.start, r.end), ownerCell(r.ownerName, r.ownerUsername),
                        r.identifier, label('vehicleType', r.vehicleType)]);
                    panel.replaceChildren(table(['When', 'Owner', 'Plate', 'Type'], rows, 'Nothing to show.'));
                }
            } catch (err) {
                panel.replaceChildren(notice(err.message, 'error'));
            }
        }

        async function refresh() {
            try {
                render(await Api.get('/api/manager/dashboard'));
            } catch (err) {
                body.replaceChildren(notice(err.message, 'error'));
            }
        }

        refresh();
        const timer = setInterval(refresh, 10000);
        return () => clearInterval(timer);
    };

    /* =====================================================================
       Manual gate: type the plate by hand (the fallback when the hardware is down)
       ===================================================================== */
    Pages.gate = function (me, root) {
        root.append(el('h1', {}, 'Gate'));
        const resultSlot = el('div', { 'aria-live': 'polite' });
        const insideSlot = el('div');

        function showResult(r) {
            const title = r.allowed ? (r.direction === 'OUT' ? 'Exit recorded' : 'Allowed') : 'Denied';
            const details = [r.ownerName, r.vehicleType ? label('vehicleType', r.vehicleType) : null, r.identifier]
                .filter(Boolean).join(', ');
            resultSlot.replaceChildren(el('div', { class: 'result ' + (r.allowed ? 'allow' : 'deny') },
                el('h2', {}, title),
                el('p', {}, r.message),
                details ? el('p', { class: 'soft' }, details) : null));
        }

        /* --- visitors --- */
        const visitorBox = errorBox();
        const visitorForm = el('form', { novalidate: true },
            visitorBox,
            field('name', 'Visitor name', 'text', { attrs: { maxlength: 120, autocomplete: 'off' } }),
            field('vehicleType', 'Vehicle type', 'select', { options: UI.typeOptions(true) }),
            field('visitorPlate', 'Plate number', 'text', { attrs: { maxlength: 30, autocapitalize: 'characters', autocomplete: 'off' } }),
            field('visitorReason', 'Reason for the visit', 'text', { attrs: { maxlength: 255, autocomplete: 'off' },
                hint: 'For example: enrollment, meeting with the registrar, delivery.' }),
            el('button', { type: 'submit', class: 'btn' }, 'Let visitor in'));

        visitorForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            clearErrors(visitorForm, visitorBox);
            const f = visitorForm.elements;
            await busy(visitorForm.querySelector('button[type="submit"]'), async () => {
                try {
                    const result = await Api.post('/api/manager/gate/visitor-entry', {
                        name: f.name.value,
                        vehicleType: f.vehicleType.value || null,
                        identifier: f.visitorPlate.value,
                        reason: f.visitorReason.value
                    });
                    showResult(result);
                    if (result.allowed) visitorForm.reset();
                    loadInside();
                } catch (err) {
                    showErrors(visitorForm, visitorBox, err.message, {
                        name: err.fieldErrors && err.fieldErrors.name,
                        vehicleType: err.fieldErrors && err.fieldErrors.vehicleType,
                        visitorPlate: err.fieldErrors && err.fieldErrors.identifier,
                        visitorReason: err.fieldErrors && err.fieldErrors.reason
                    });
                }
            });
        });

        /* --- manual override --- */
        const overrideBox = errorBox();
        const overrideForm = el('form', { novalidate: true },
            overrideBox,
            field('ovPlate', 'Plate number', 'text', { attrs: { maxlength: 30, autocapitalize: 'characters', autocomplete: 'off' } }),
            field('ovType', 'Vehicle type', 'select', { required: false, options: UI.typeOptions(true),
                hint: 'Only needed when the plate is not registered.' }),
            field('ovName', 'Name (optional)', 'text', { required: false, attrs: { maxlength: 120, autocomplete: 'off' },
                hint: 'Only used when the plate is not registered.' }),
            field('ovReason', 'Reason', 'text', { attrs: { maxlength: 255, autocomplete: 'off' },
                hint: 'Required. For example: gate system down, ambulance, special permission.' }),
            el('button', { type: 'submit', class: 'btn danger' }, 'Record override entry'));

        overrideForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            clearErrors(overrideForm, overrideBox);
            const f = overrideForm.elements;
            if (!window.confirm('Let this vehicle in without the normal checks? It is saved in the log as a manual override.')) return;
            await busy(overrideForm.querySelector('button[type="submit"]'), async () => {
                try {
                    const result = await Api.post('/api/manager/gate/override', {
                        identifier: f.ovPlate.value,
                        vehicleType: f.ovType.value || null,
                        name: f.ovName.value,
                        reason: f.ovReason.value
                    });
                    showResult(result);
                    overrideForm.reset();
                    loadInside();
                } catch (err) {
                    showErrors(overrideForm, overrideBox, err.message, {
                        ovPlate: err.fieldErrors && err.fieldErrors.identifier,
                        ovType: err.fieldErrors && err.fieldErrors.vehicleType,
                        ovName: err.fieldErrors && err.fieldErrors.name,
                        ovReason: err.fieldErrors && err.fieldErrors.reason
                    });
                }
            });
        });

        /* --- inside now --- */
        // One list: everyone inside right now (with a Record exit button) plus the few vehicles that just left.
        async function loadInside() {
            try {
                const [inside, dash] = await Promise.all([
                    Api.get('/api/manager/gate/inside'),
                    Api.get('/api/manager/dashboard')]);
                const insideIds = new Set(inside.map((l) => l.id));
                const left = (dash.recentLogs || []).filter((l) => l.exitTime && !insideIds.has(l.id)).slice(0, 5);
                const all = inside.concat(left).sort((x, y) => (y.entryTime || '').localeCompare(x.entryTime || ''));
                const rows = all.map((l) => [
                    UI.when(l.entryTime),
                    l.exitTime ? UI.when(l.exitTime) : badge('Inside now', 'wait'),
                    l.identifier || el('span', { class: 'soft' }, 'No plate'),
                    el('span', {}, l.name || '', l.visitor ? [' ', badge('Visitor')] : ''),
                    el('span', {}, label('vehicleType', l.vehicleType), l.area === 'FACULTY' ? [el('br'), el('span', { class: 'soft' }, label('area', l.area))] : ''),
                    noteCell(l),
                    l.exitTime ? '' : el('button', { type: 'button', class: 'btn small quiet', onclick: (e) => exitVisit(l, e.currentTarget) }, 'Record exit')
                ]);
                insideSlot.replaceChildren(table(['Entered', 'Left', 'Plate', 'Name', 'Type', 'Note', ''], rows, 'No vehicles yet.'));
            } catch (err) {
                insideSlot.replaceChildren(notice(err.message, 'error'));
            }
        }

        async function exitVisit(entry, button) {
            await busy(button, async () => {
                try {
                    await Api.post('/api/manager/gate/exit/' + entry.id);
                } catch (err) {
                    window.alert(err.message);
                }
                loadInside();
            });
        }

        /* --- gate hardware status (refreshes every 10 seconds) --- */
        const hardwareSlot = el('div', {}, loading());
        async function loadHardware() {
            try {
                const dash = await Api.get('/api/manager/dashboard');
                const devices = dash.devices || [];
                hardwareSlot.replaceChildren(devices.length === 0
                    ? notice('No gate hardware has connected since the server started. Use the forms below to type plates by hand.')
                    : el('dl', { class: 'facts' }, ...devices.map((dev) => el('div', {}, el('dt', {}, dev.deviceId),
                        el('dd', {}, badge(dev.online ? 'Online' : 'Offline', dev.online ? 'good' : 'bad'),
                            ' last heard ' + dev.secondsAgo + (dev.secondsAgo === 1 ? ' second ago' : ' seconds ago'))))));
            } catch (err) {
                hardwareSlot.replaceChildren(notice(err.message, 'error'));
            }
        }
        loadHardware();
        const hardwareTimer = setInterval(() => { loadHardware(); loadInside(); }, 10000);

        root.append(
            resultSlot,
            el('h2', { class: 'section first' }, 'Gate hardware'),
            hardwareSlot,
            el('h2', { class: 'section' }, 'Vehicles in and out'),
            insideSlot,
            el('h2', { class: 'section' }, 'Visitor'),
            visitorForm,
            me.role === 'PARKING_MANAGER'
                ? el('details', { class: 'override', open: true },
                    el('summary', {}, 'Manual override'),
                    el('p', { class: 'soft' }, 'Lets a vehicle in without the normal checks. Every override is marked in the log with your reason.'),
                    overrideForm)
                : '',
            '');
        loadInside();
        return () => clearInterval(hardwareTimer);
    };

    /* =====================================================================
       Vehicle review
       ===================================================================== */
    Pages.vehicleReview = function (me, root) {
        root.append(el('h1', {}, 'Vehicles'));
        const filter = field('status', 'Show', 'select', {
            required: false, value: 'PENDING',
            options: [
                { value: 'PENDING', label: 'Waiting for approval' },
                { value: 'APPROVED', label: 'Approved' },
                { value: 'REJECTED', label: 'Rejected' },
                { value: 'REVOKED', label: 'Revoked' },
                { value: '', label: 'All vehicles' }
            ]
        });
        const select = filter.querySelector('select');
        const slot = el('div');
        root.append(el('div', { class: 'toolbar' }, filter), slot);

        async function load() {
            slot.replaceChildren(loading());
            try {
                const query = select.value ? '?status=' + encodeURIComponent(select.value) : '';
                const items = await Api.get('/api/manager/vehicles' + query);
                const rows = items.map((v) => [
                    ownerCell(v.ownerName, v.ownerUsername),
                    label('userType', v.ownerType),
                    v.identifier,
                    label('vehicleType', v.vehicleType),
                    [v.make, v.model, v.color].filter(Boolean).join(', '),
                    badge(label('vehicleStatus', v.status), VEHICLE_TONE[v.status]),
                    actionsFor(v)
                ]);
                slot.replaceChildren(table(
                    ['Owner', 'Type', 'Plate', 'Vehicle', 'Details', 'Status', ''], rows,
                    select.value === 'PENDING' ? 'No vehicles are waiting for approval.' : 'No vehicles found.'));
            } catch (err) {
                slot.replaceChildren(notice(err.message, 'error'));
            }
        }

        function act(vehicle, action, confirmText, button) {
            return async () => {
                if (confirmText && !window.confirm(confirmText)) return;
                await busy(button, async () => {
                    try {
                        await Api.post('/api/manager/vehicles/' + vehicle.id + '/' + action);
                    } catch (err) {
                        window.alert(err.message);
                    }
                    load();
                });
            };
        }

        function actionButton(vehicle, action, text, cls, confirmText) {
            const button = el('button', { type: 'button', class: 'btn small ' + cls }, text);
            button.addEventListener('click', act(vehicle, action, confirmText, button));
            return button;
        }

        function actionsFor(v) {
            const buttons = [];
            if (v.status === 'PENDING') {
                buttons.push(actionButton(v, 'approve', 'Approve', ''));
                buttons.push(actionButton(v, 'reject', 'Reject', 'quiet', 'Reject ' + v.identifier + '?'));
            } else if (v.status === 'APPROVED') {
                buttons.push(actionButton(v, 'revoke', 'Revoke', 'danger',
                    'Revoke ' + v.identifier + '? Its active reservations will be cancelled.'));
            } else {
                buttons.push(actionButton(v, 'approve', 'Approve', 'quiet'));
            }
            return el('div', { class: 'actions' }, buttons);
        }

        select.addEventListener('change', load);
        load();
    };

    /* =====================================================================
       Reservations (all users, one day at a time; approved automatically)
       ===================================================================== */
    Pages.managerReservations = function (me, root) {
        root.append(el('h1', {}, 'Reservations'));
        const dateField = field('date', 'Day', 'date', { required: false, value: UI.toDateInputValue(UI.now()) });
        const statusField = field('status', 'Status', 'select', {
            required: false, value: 'ACTIVE',
            options: [
                { value: 'ACTIVE', label: 'Approved' },
                { value: 'EXPIRED', label: 'Expired' },
                { value: 'COMPLETED', label: 'Completed' },
                { value: 'CANCELLED', label: 'Cancelled' },
                { value: 'NO_SHOW', label: 'No show' },
                { value: '', label: 'All' }
            ]
        });
        const dateInput = dateField.querySelector('input');
        const statusSelect = statusField.querySelector('select');
        const slot = el('div');
        root.append(el('div', { class: 'toolbar' }, dateField, statusField), slot);

        async function load() {
            slot.replaceChildren(loading());
            try {
                const params = new URLSearchParams();
                if (dateInput.value) params.set('date', dateInput.value);
                if (statusSelect.value) params.set('status', statusSelect.value);
                const items = await Api.get('/api/manager/reservations?' + params.toString());
                const rows = items.map((r) => [
                    UI.formatRange(r.start, r.end),
                    ownerCell(r.ownerName, r.ownerUsername),
                    r.identifier,
                    label('vehicleType', r.vehicleType),
                    badge(label('reservationStatus', r.status), RESERVATION_TONE[r.status]),
                    r.status === 'ACTIVE'
                        ? el('button', { type: 'button', class: 'btn small quiet', onclick: (e) => cancel(r, e.currentTarget) }, 'Cancel')
                        : ''
                ]);
                slot.replaceChildren(table(['When', 'Owner', 'Plate', 'Type', 'Status', ''], rows,
                    'No reservations match.'));
            } catch (err) {
                slot.replaceChildren(notice(err.message, 'error'));
            }
        }

        async function cancel(reservation, button) {
            if (!window.confirm('Cancel the reservation of ' + reservation.ownerName + ' (' + reservation.identifier + ')?')) return;
            await busy(button, async () => {
                try {
                    await Api.post('/api/manager/reservations/' + reservation.id + '/cancel');
                } catch (err) {
                    window.alert(err.message);
                }
                load();
            });
        }

        dateInput.addEventListener('change', load);
        statusSelect.addEventListener('change', load);
        load();
    };

    /* =====================================================================
       Activity (longer lists than the dashboard)
       ===================================================================== */
    Pages.activity = function (me, root) {
        root.append(el('h1', {}, 'Activity'));
        const logSlot = el('div', {}, loading());
        const deniedSlot = el('div', {}, loading());

        // Download every vehicle that parked in a date range as a table file (CSV: opens in Excel or Google Sheets).
        const today = UI.now();
        const iso = (d) => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
        const monthAgo = new Date(today.getTime() - 30 * 86400000);
        const fromField = field('exportFrom', 'From', 'date', { value: iso(monthAgo), attrs: { max: iso(today) } });
        const toField = field('exportTo', 'To', 'date', { value: iso(today) });
        const exportBox = errorBox();
        const exportButton = el('button', { type: 'button', class: 'btn' }, 'Download table (CSV)');
        exportButton.addEventListener('click', async () => {
            clearErrors(exportForm, exportBox);
            const from = fromField.querySelector('input').value;
            const to = toField.querySelector('input').value;
            if (!from || !to) { showErrors(exportForm, exportBox, 'Choose both dates.', {}); return; }
            await busy(exportButton, async () => {
                try {
                    await Api.download('/api/manager/logs/export?from=' + from + '&to=' + to, 'parking-log.csv');
                } catch (err) {
                    showErrors(exportForm, exportBox, err.message, {});
                }
            });
        });
        const exportForm = el('form', { novalidate: true },
            el('p', { class: 'soft intro' }, 'Every vehicle that parked between two dates, one row per visit: who, plate, entry and exit times, reservation, and notes. '
                + 'Open the file in Excel or Google Sheets. Visits older than 30 days are removed automatically, so download before then.'),
            exportBox,
            el('div', { class: 'toolbar' }, fromField, toField, el('div', { class: 'actions' }, exportButton)));
        exportForm.addEventListener('submit', (e) => e.preventDefault());

        // Filters work on the entries already loaded (the 100 most recent), so they respond instantly.
        const WHO = [
            { value: '', label: 'Everyone' },
            { value: 'VISITOR', label: 'Visitors' },
            { value: 'STUDENT', label: 'Students' },
            { value: 'FACULTY', label: 'Faculty' },
            { value: 'PERSONNEL', label: 'Personnel' }
        ];
        const whoField = field('who', 'Who', 'select', { required: false, value: '', options: WHO });
        const vehicleField = field('vehicle', 'Vehicle', 'select', {
            required: false, value: '',
            options: [{ value: '', label: 'All vehicles' }, ...UI.typeOptions(false)]
        });
        const whoSelect = whoField.querySelector('select');
        const vehicleSelect = vehicleField.querySelector('select');
        let allLogs = [];

        function applyFilters() {
            if (allLogs.length === 0) {
                logSlot.replaceChildren(table(LOG_HEADERS, [], 'Nothing recorded yet.'));
                return;
            }
            const who = whoSelect.value;
            const vehicle = vehicleSelect.value;
            const shown = allLogs.filter((l) => {
                if (who === 'VISITOR' && !l.visitor) return false;
                if (who && who !== 'VISITOR' && l.ownerType !== who) return false;
                if (vehicle && l.vehicleType !== vehicle) return false;
                return true;
            });
            logSlot.replaceChildren(
                el('p', { class: 'soft' }, shown.length + ' of ' + allLogs.length + ' entries shown.'),
                table(LOG_HEADERS, logRows(shown), 'No entries match these filters.'));
        }
        whoSelect.addEventListener('change', applyFilters);
        vehicleSelect.addEventListener('change', applyFilters);

        root.append(
            el('h2', { class: 'section first' }, 'Download'), exportForm,
            el('h2', { class: 'section' }, 'Entries and exits'),
            el('div', { class: 'toolbar' }, whoField, vehicleField),
            logSlot,
            el('h2', { class: 'section' }, 'Refused entries'), deniedSlot);

        Api.get('/api/manager/logs?limit=100')
            .then((items) => { allLogs = items; applyFilters(); })
            .catch((err) => logSlot.replaceChildren(notice(err.message, 'error')));
        Api.get('/api/manager/denied?limit=100')
            .then((items) => deniedSlot.replaceChildren(table(DENIED_HEADERS, deniedRows(items), 'No refused entries.')))
            .catch((err) => deniedSlot.replaceChildren(notice(err.message, 'error')));
    };

    /* =====================================================================
       Capacity (super admin)
       ===================================================================== */
    Pages.capacity = function (me, root) {
        root.append(el('h1', {}, 'Capacity'));
        root.append(el('p', { class: 'soft intro' },
            'How many vehicles of each type each part of the lot can hold. Changing a number affects new reservations only. '
            + 'Faculty and personnel use the faculty parking when it has spaces for their vehicle type. '
            + 'Set a faculty number to 0 to send that vehicle type to the general lot instead.'));
        const body = el('div', {}, loading());
        root.append(body);

        function capacityForm(c) {
            const id = 'capacity-' + c.area + '-' + c.vehicleType;
            const box = errorBox();
            const done = el('span', { class: 'saved', role: 'status' });
            const form = el('form', { class: 'capacity-form', novalidate: true },
                box,
                field(id, label('vehicleType', c.vehicleType), 'number', {
                    value: c.capacity, attrs: { min: 0, max: 1000, step: 1 }
                }),
                el('div', { class: 'actions' }, el('button', { type: 'submit', class: 'btn small' }, 'Save'), done));

            form.addEventListener('submit', async (event) => {
                event.preventDefault();
                clearErrors(form, box);
                done.textContent = '';
                const input = form.elements[id];
                await busy(form.querySelector('button'), async () => {
                    try {
                        const saved = await Api.put('/api/admin/capacity/' + c.area + '/' + c.vehicleType,
                            { capacity: input.value === '' ? null : Number(input.value) });
                        input.value = saved.capacity;
                        done.textContent = 'Saved';
                    } catch (err) {
                        // The server names the field "capacity"; show it next to our input.
                        const fieldErrors = {};
                        if (err.fieldErrors && err.fieldErrors.capacity) fieldErrors[id] = err.fieldErrors.capacity;
                        showErrors(form, box, err.message, fieldErrors);
                    }
                });
            });
            return form;
        }

        Api.get('/api/capacity').then((items) => {
            const parts = [];
            ['GENERAL', 'FACULTY'].forEach((area) => {
                parts.push(el('h2', { class: 'section' }, label('area', area)));
                parts.push(...items.filter((c) => c.area === area).map(capacityForm));
            });
            body.replaceChildren(...parts);
        }).catch((err) => body.replaceChildren(notice(err.message, 'error')));
    };

    /* =====================================================================
       Penalties (no-show warnings and overstays)
       ===================================================================== */
    Pages.penalties = function (me, root) {
        root.append(el('h1', {}, 'Penalties'));
        root.append(el('p', { class: 'soft intro' },
            'Warnings for reservations nobody showed up for, and overstays. Only the super admin can remove a user\'s penalties (Users page).'));
        const slot = el('div', {}, loading());
        root.append(slot);
        Api.get('/api/manager/penalties?limit=100').then((items) => {
            const rows = items.map((p) => [
                UI.when(p.createdAt),
                ownerCell(p.userName, p.username),
                badge(label('penaltyType', p.type), p.type === 'OVERSTAY' ? 'bad' : 'wait'),
                p.description
            ]);
            slot.replaceChildren(table(['When', 'Person', 'Type', 'Details'], rows, 'No penalties have been given yet.'));
        }).catch((err) => slot.replaceChildren(notice(err.message, 'error')));
    };
})();
