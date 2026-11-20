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

    function noteCell(l) {
        const parts = [];
        if (l.overstay) parts.push(badge('Overstay', 'bad'));
        if (l.visitReason) parts.push(el('span', { class: 'soft' }, 'Visit: ' + l.visitReason));
        if (l.override) {
            const b = badge('Manual override', 'wait');
            if (l.overrideReason) b.title = l.overrideReason;
            parts.push(b);
            if (l.overrideReason) parts.push(el('span', { class: 'soft' }, l.overrideReason));
        }
        return parts.length ? el('div', { class: 'actions' }, parts) : '';
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

        function meter(inside, capacity, name) {
            const pct = capacity > 0 ? Math.min(100, Math.round(inside * 100 / capacity)) : 100;
            const tone = pct >= 100 ? ' full' : (pct >= 80 ? ' busy' : '');
            return el('div', {
                class: 'meter' + tone, role: 'meter', 'aria-label': name + ' spaces in use',
                'aria-valuemin': 0, 'aria-valuemax': capacity, 'aria-valuenow': inside
            }, el('div', { class: 'meter-fill', style: 'width:' + pct + '%' }));
        }

        function render(d) {
            const bays = d.types.map((t) => {
                const name = (t.area === 'FACULTY' ? label('area', t.area) + ': ' : '') + label('vehicleType', t.vehicleType);
                return el('div', { class: 'bay-row' },
                    el('div', { class: 'bay-head' },
                        el('strong', {}, name),
                        el('span', {}, t.inside + ' of ' + t.capacity + ' in use')),
                    meter(t.inside, t.capacity, name),
                    el('p', { class: 'soft' }, t.available + ' free. ' + t.reservedNow + ' reserved right now.'));
            });

            const overstayLine = d.overstayingNow > 0
                ? el('span', {}, badge(String(d.overstayingNow), 'bad'), ' ', el('a', { href: '#gate' }, 'See who is inside'))
                : '0';

            const devices = d.devices || [];
            const hardware = devices.length === 0
                ? [notice('No gate hardware has connected since the server started. Use the Gate page to type plates by hand.')]
                : [el('dl', { class: 'facts' }, ...devices.map((dev) => fact(dev.deviceId,
                    el('span', {}, badge(dev.online ? 'Online' : 'Offline', dev.online ? 'good' : 'bad'),
                        ' last heard ' + dev.secondsAgo + (dev.secondsAgo === 1 ? ' second ago' : ' seconds ago')))))];

            const waitingLine = d.pendingReservations > 0
                ? el('span', {}, String(d.pendingReservations), ' ', el('a', { href: '#approvals' }, 'Review them'))
                : '0';

            const pendingLine = d.pendingVehicles > 0
                ? el('span', {}, String(d.pendingVehicles), ' ', el('a', { href: '#vehicle-review' }, 'Review them'))
                : '0';

            body.replaceChildren(
                el('h2', { class: 'section first' }, 'Spaces in use right now'),
                ...bays,
                el('h2', { class: 'section' }, 'Gate hardware'),
                ...hardware,
                el('h2', { class: 'section' }, 'Today'),
                el('dl', { class: 'facts' },
                    fact('Reservations starting today', String(d.reservationsToday)),
                    fact('Vehicles that entered', String(d.entriesToday)),
                    fact('Vehicles inside past their reservation', overstayLine),
                    fact('Entries refused', String(d.deniedToday)),
                    fact('Vehicles waiting for approval', pendingLine),
                    fact('Reservations waiting for approval', waitingLine)),
                el('h2', { class: 'section' }, 'Latest entries and exits'),
                table(LOG_HEADERS, logRows(d.recentLogs), 'Nothing recorded yet.'),
                el('h2', { class: 'section' }, 'Latest refused entries'),
                table(DENIED_HEADERS, deniedRows(d.recentDenied), 'No refused entries.'),
                el('p', { class: 'soft updated' }, 'Updated ' + UI.formatTime(UI.now()) + '. This page refreshes every 10 seconds.'));
        }

        function fact(name, value) {
            return el('div', {}, el('dt', {}, name), el('dd', {}, value));
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
        root.append(el('p', { class: 'soft intro' },
            'Use this page when the gate hardware is offline or not working: let a visitor in, or record a manual override. '
            + 'Record exits in the list at the bottom.'));

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
        async function loadInside() {
            try {
                const items = await Api.get('/api/manager/gate/inside');
                const rows = items.map((l) => [
                    el('span', {}, l.name || '', l.visitor ? [' ', badge('Visitor')] : ''),
                    l.identifier || el('span', { class: 'soft' }, 'No plate'),
                    el('span', {}, label('vehicleType', l.vehicleType), l.area === 'FACULTY' ? [el('br'), el('span', { class: 'soft' }, label('area', l.area))] : ''),
                    UI.when(l.entryTime),
                    noteCell(l),
                    el('button', { type: 'button', class: 'btn small quiet', onclick: (e) => exitVisit(l, e.currentTarget) }, 'Record exit')
                ]);
                insideSlot.replaceChildren(table(['Name', 'Plate', 'Type', 'Entered', 'Note', ''], rows, 'Nobody is inside right now.'));
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

        root.append(
            resultSlot,
            el('h2', { class: 'section first' }, 'Visitor (no account, no reservation)'),
            visitorForm,
            el('details', { class: 'override', open: true },
                el('summary', {}, 'Manual override'),
                el('p', { class: 'soft' }, 'Lets a vehicle in without the normal checks. Every override is marked in the log with your reason.'),
                overrideForm),
            el('h2', { class: 'section' }, 'Inside now'),
            insideSlot);
        loadInside();
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
       Approvals: reservation requests waiting for a manager
       ===================================================================== */
    Pages.approvals = function (me, root) {
        root.append(el('h1', {}, 'Approvals'),
            el('p', { class: 'intro soft' }, 'Users can reserve once a day. Each request needs your approval before it can be used at the gate.'));
        const slot = el('div');
        root.append(slot);

        async function load() {
            slot.replaceChildren(loading());
            try {
                const items = await Api.get('/api/manager/reservations/pending');
                const rows = items.map((r) => [
                    UI.formatRange(r.start, r.end),
                    ownerCell(r.ownerName, r.ownerUsername),
                    r.identifier,
                    label('vehicleType', r.vehicleType),
                    UI.when(r.createdAt),
                    el('div', { class: 'actions' },
                        el('button', { type: 'button', class: 'btn small', onclick: (e) => decide(r, 'approve', e.currentTarget) }, 'Approve'),
                        el('button', { type: 'button', class: 'btn small quiet', onclick: (e) => decide(r, 'reject', e.currentTarget) }, 'Reject'))
                ]);
                slot.replaceChildren(table(['When', 'Owner', 'Plate', 'Type', 'Requested', ''], rows,
                    'Nothing is waiting for approval.'));
            } catch (err) {
                slot.replaceChildren(notice(err.message, 'error'));
            }
        }

        async function decide(reservation, action, button) {
            let body = null;
            if (action === 'reject') {
                const reason = window.prompt('Reason for rejecting (optional, the user will see it):', '');
                if (reason === null) return; // cancelled the prompt
                body = { note: reason };
            }
            await busy(button, async () => {
                try {
                    await Api.post('/api/manager/reservations/' + reservation.id + '/' + action, body || undefined);
                } catch (err) {
                    window.alert(err.message);
                }
                await load();
                document.dispatchEvent(new Event('parking:changed'));
            });
        }

        load();
    };

    /* =====================================================================
       Reservations (all users, one day at a time)
       ===================================================================== */
    Pages.managerReservations = function (me, root) {
        root.append(el('h1', {}, 'Reservations'));
        const dateField = field('date', 'Day', 'date', { required: false, value: UI.toDateInputValue(UI.now()) });
        const statusField = field('status', 'Status', 'select', {
            required: false, value: 'ACTIVE',
            options: [
                { value: 'ACTIVE', label: 'Approved' },
                { value: 'PENDING', label: 'Waiting for approval' },
                { value: 'REJECTED', label: 'Rejected' },
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
                    r.status === 'ACTIVE' || r.status === 'PENDING'
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
        root.append(
            el('h2', { class: 'section first' }, 'Entries and exits'), logSlot,
            el('h2', { class: 'section' }, 'Refused entries'), deniedSlot);

        Api.get('/api/manager/logs?limit=100')
            .then((items) => logSlot.replaceChildren(table(LOG_HEADERS, logRows(items), 'Nothing recorded yet.')))
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
