/* Pages for regular users: my vehicle, reservations, log book. */
(function () {
    'use strict';

    const { el, notice, badge, loading, table, field, errorBox, clearErrors, showErrors, busy, label } = UI;
    const Pages = (window.Pages = window.Pages || {});

    const STATUS_TONE = { APPROVED: 'good', PENDING: 'wait', REJECTED: 'bad', REVOKED: 'bad' };
    const RESERVATION_TONE = { PENDING: 'wait', ACTIVE: 'good', REJECTED: 'bad', EXPIRED: '', COMPLETED: '', CANCELLED: '', NO_SHOW: 'bad' };

    /* =====================================================================
       My vehicle
       ===================================================================== */
    Pages.vehicle = function (me, root) {
        root.append(el('h1', {}, 'My vehicle'));
        const body = el('div');
        root.append(body);

        async function refresh(message) {
            body.replaceChildren(loading());
            try {
                const vehicle = await Api.get('/api/vehicles/me');
                showVehicle(vehicle, message);
            } catch (err) {
                if (err.status === 404 && err.code === 'NO_VEHICLE') showRegister();
                else body.replaceChildren(notice(err.message, 'error'));
            }
        }

        function showRegister() {
            body.replaceChildren(
                notice('You have not registered a vehicle yet. Register one to start reserving parking.'),
                vehicleForm(null));
        }

        function showVehicle(v, message) {
            const parts = [];
            if (message) parts.push(notice(message));
            parts.push(statusNotice(v));
            parts.push(el('dl', { class: 'facts' },
                row('Vehicle type', label('vehicleType', v.vehicleType)),
                row('Plate number', v.identifier),
                v.make ? row('Make', v.make) : null,
                v.model ? row('Model', v.model) : null,
                v.color ? row('Color', v.color) : null,
                el('div', {}, el('dt', {}, 'Status'),
                    el('dd', {}, badge(label('vehicleStatus', v.status), STATUS_TONE[v.status])))));

            if (v.status !== 'REVOKED') {
                parts.push(el('h2', { class: 'section' }, 'Edit details'));
                parts.push(vehicleForm(v));
            }
            body.replaceChildren(...parts);
        }

        function statusNotice(v) {
            switch (v.status) {
                case 'PENDING':
                    return notice('Waiting for approval by the parking office. You can reserve parking once it is approved.');
                case 'APPROVED':
                    return el('div', { class: 'notice', role: 'status' },
                        'Approved. You can now ', el('a', { href: '#reservations' }, 'reserve parking'), '.');
                case 'REJECTED':
                    return notice('Rejected by the parking office. Fix the details below and save to send it for approval again.', 'caution');
                default:
                    return notice('This vehicle was revoked by the parking office. Contact them to restore it.', 'error');
            }
        }

        function row(name, value) {
            return el('div', {}, el('dt', {}, name), el('dd', {}, value));
        }

        function vehicleForm(existing) {
            const box = errorBox();
            const form = el('form', { novalidate: true },
                box,
                field('vehicleType', 'Vehicle type', 'select',
                    { options: UI.typeOptions(true), value: existing ? existing.vehicleType : '' }),
                field('identifier', 'Plate number', 'text', {
                    value: existing ? existing.identifier : '',
                    hint: 'Letters and numbers only, for example ABC 1234. For an e-trike without a plate, use its body number.',
                    attrs: { maxlength: 30, autocapitalize: 'characters' }
                }),
                field('make', 'Make (optional)', 'text', { required: false, value: existing && existing.make, attrs: { maxlength: 50 } }),
                field('model', 'Model (optional)', 'text', { required: false, value: existing && existing.model, attrs: { maxlength: 50 } }),
                field('color', 'Color (optional)', 'text', { required: false, value: existing && existing.color, attrs: { maxlength: 30 } }),
                existing ? el('p', { class: 'hint-block' },
                    'Changing the plate number or vehicle type sends the vehicle for approval again.') : null,
                el('button', { type: 'submit', class: 'btn' }, existing ? 'Save changes' : 'Register vehicle'));

            form.addEventListener('submit', async (event) => {
                event.preventDefault();
                clearErrors(form, box);
                const f = form.elements;
                const button = form.querySelector('button[type="submit"]');
                await busy(button, async () => {
                    try {
                        await (existing ? Api.put : Api.post)('/api/vehicles/me', {
                            vehicleType: f.vehicleType.value || null,
                            identifier: f.identifier.value,
                            make: f.make.value,
                            model: f.model.value,
                            color: f.color.value
                        });
                        await refresh(existing ? 'Changes saved.' : 'Vehicle registered.');
                    } catch (err) {
                        showErrors(form, box, err.message, err.fieldErrors);
                    }
                });
            });
            return form;
        }

        refresh();
    };

    /* =====================================================================
       Reservations
       ===================================================================== */
    /* =====================================================================
       Reservation calendar: tap one day, or the first and last day of a run
       ===================================================================== */
    const MAX_DAYS_AHEAD = 7; // must match app.reservation.max-days-ahead
    const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    const monthFmt = new Intl.DateTimeFormat('en-PH', { month: 'long', year: 'numeric' });
    const dayShortFmt = new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric' });
    const dayShort = (d) => dayShortFmt.format(d);

    /** The given day at an "HH:mm" time, in local time. */
    function atTime(day, hhmm) {
        const [h, m] = hhmm.split(':').map(Number);
        return new Date(day.getFullYear(), day.getMonth(), day.getDate(), h, m);
    }

    function buildRangeCalendar(today, onChange) {
        const lastDay = new Date(today.getFullYear(), today.getMonth(), today.getDate() + MAX_DAYS_AHEAD);
        let shown = new Date(today.getFullYear(), today.getMonth(), 1);
        let first = null;
        let last = null;

        const title = el('strong', { class: 'cal-title' });
        const grid = el('div', { class: 'cal-grid' });
        const hint = el('p', { class: 'soft cal-hint' },
            'Tap a day to reserve it. For several days in a row, tap the first day, then the last day.');
        const prev = el('button', { type: 'button', class: 'btn small quiet', 'aria-label': 'Previous month' }, '\u2039');
        const next = el('button', { type: 'button', class: 'btn small quiet', 'aria-label': 'Next month' }, '\u203A');
        prev.addEventListener('click', () => { shown = new Date(shown.getFullYear(), shown.getMonth() - 1, 1); draw(); });
        next.addEventListener('click', () => { shown = new Date(shown.getFullYear(), shown.getMonth() + 1, 1); draw(); });

        /** The selected days, first to last (one day if only one was tapped). */
        function days() {
            if (!first) return [];
            const end = last || first;
            const out = [];
            for (let d = new Date(first); d <= end; d = new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1)) {
                out.push(new Date(d));
            }
            return out;
        }

        function tap(date) {
            if (!first || last) { first = date; last = null; }   // start a new selection
            else if (date < first) { last = first; first = date; } // tapped earlier: that becomes the first day
            else { last = date; }                                  // second tap: the last day (same day = one day)
            draw();
            onChange();
        }

        function draw() {
            title.textContent = monthFmt.format(shown);
            prev.disabled = shown <= new Date(today.getFullYear(), today.getMonth(), 1);
            next.disabled = new Date(shown.getFullYear(), shown.getMonth() + 1, 1) > lastDay;

            const cells = WEEKDAYS.map((n) => el('span', { class: 'cal-weekday', 'aria-hidden': 'true' }, n));
            const offset = new Date(shown.getFullYear(), shown.getMonth(), 1).getDay();
            for (let i = 0; i < offset; i++) cells.push(el('span', { class: 'cal-blank' }));

            const count = new Date(shown.getFullYear(), shown.getMonth() + 1, 0).getDate();
            for (let n = 1; n <= count; n++) {
                const date = new Date(shown.getFullYear(), shown.getMonth(), n);
                const open = date >= today && date <= lastDay;
                const isEdge = (first && date.getTime() === first.getTime()) || (last && date.getTime() === last.getTime());
                const inside = first && last && date > first && date < last;
                const classes = ['cal-day'];
                if (date.getTime() === today.getTime()) classes.push('today');
                if (isEdge) classes.push('picked');
                if (inside) classes.push('in-range');
                const button = el('button', {
                    type: 'button', class: classes.join(' '),
                    'aria-label': dayShort(date) + (isEdge ? ', selected' : '')
                }, String(n));
                button.disabled = !open;
                button.addEventListener('click', () => tap(date));
                cells.push(button);
            }
            grid.replaceChildren(...cells);
        }

        draw();
        return {
            element: el('div', { class: 'calendar' }, el('div', { class: 'cal-head' }, prev, title, next), grid, hint),
            days,
            clear() { first = null; last = null; draw(); }
        };
    }

    Pages.reservations = function (me, root) {
        const spacesSlot = el('div', {}, loading());
        const top = el('div');
        const list = el('div');
        root.append(
            el('h1', {}, 'Reservations'),
            el('h2', { class: 'section first' }, 'Spaces right now'),
            spacesSlot,
            el('h2', { class: 'section' }, 'Reserve parking'),
            top,
            list);
        let vehicle = null;

        async function init() {
            top.replaceChildren(loading());
            try {
                vehicle = await Api.get('/api/vehicles/me');
            } catch (err) {
                if (err.status === 404 && err.code === 'NO_VEHICLE') {
                    top.replaceChildren(el('div', { class: 'notice caution', role: 'status' },
                        'Register your vehicle before you reserve parking. ',
                        el('a', { href: '#vehicle' }, 'Register a vehicle'), '.'));
                } else {
                    top.replaceChildren(notice(err.message, 'error'));
                }
                loadList();
                return;
            }

            if (vehicle.status !== 'APPROVED') {
                top.replaceChildren(el('div', { class: 'notice caution', role: 'status' },
                    'Your vehicle is not approved yet (' + label('vehicleStatus', vehicle.status).toLowerCase()
                    + '). You can reserve parking once it is approved. ',
                    el('a', { href: '#vehicle' }, 'View my vehicle'), '.'));
            } else {
                top.replaceChildren(buildForm());
                checkAvailability();
            }
            loadList();
        }

        function buildForm() {
            const now = UI.now();
            const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
            const box = errorBox();
            const doneBox = el('div', { class: 'notice', role: 'status', hidden: true });
            const availability = el('p', { class: 'availability', role: 'status' });
            const cal = buildRangeCalendar(today, () => check());

            const form = el('form', { novalidate: true },
                box,
                doneBox,
                el('p', { class: 'soft' }, 'Reserving a space for your ', label('vehicleType', vehicle.vehicleType).toLowerCase(),
                    ' (', vehicle.identifier, '). One reservation per day, and no approval is needed.'),
                cal.element,
                el('div', { class: 'toolbar' },
                    field('arrive', 'Arrive', 'time', { value: '08:00' }),
                    field('leave', 'Leave', 'time', { value: '10:00' })),
                availability,
                el('button', { type: 'submit', class: 'btn' }, 'Reserve parking'));

            async function check() {
                const f = form.elements;
                const days = cal.days();
                availability.className = 'availability';
                if (days.length === 0 || !f.arrive.value || !f.leave.value) { availability.textContent = ''; return; }
                if (f.leave.value <= f.arrive.value) {
                    availability.className = 'availability bad';
                    availability.textContent = 'Leave must be after Arrive.';
                    return;
                }
                availability.textContent = 'Checking spaces...';
                const full = [];
                try {
                    for (const day of days) {
                        const a = await Api.get('/api/reservations/availability?vehicleType=' + encodeURIComponent(vehicle.vehicleType)
                            + '&start=' + encodeURIComponent(UI.toInputValue(atTime(day, f.arrive.value)))
                            + '&end=' + encodeURIComponent(UI.toInputValue(atTime(day, f.leave.value))));
                        if (a.remaining === 0) full.push(dayShort(day));
                    }
                    if (full.length === 0) {
                        availability.textContent = days.length === 1
                            ? 'Space is free on that day.'
                            : 'Space is free on all ' + days.length + ' days.';
                    } else {
                        availability.className = 'availability bad';
                        availability.textContent = 'No space on: ' + full.join(', ') + '.';
                    }
                } catch (err) {
                    availability.className = 'availability bad';
                    availability.textContent = err.message;
                }
            }
            checkAvailability = check;
            form.elements.arrive.addEventListener('change', check);
            form.elements.leave.addEventListener('change', check);

            form.addEventListener('submit', async (event) => {
                event.preventDefault();
                clearErrors(form, box);
                doneBox.hidden = true;
                const days = cal.days();
                const f = form.elements;
                if (days.length === 0) { showErrors(form, box, 'Pick a day on the calendar first.', {}); return; }
                if (!f.arrive.value || !f.leave.value || f.leave.value <= f.arrive.value) {
                    showErrors(form, box, 'Leave must be after Arrive.', {});
                    return;
                }
                const button = form.querySelector('button[type="submit"]');
                await busy(button, async () => {
                    const done = [];
                    const failed = [];
                    // One reservation per day, so the one-per-day rule applies to each day separately.
                    for (const day of days) {
                        try {
                            await Api.post('/api/reservations', {
                                start: UI.toInputValue(atTime(day, f.arrive.value)),
                                end: UI.toInputValue(atTime(day, f.leave.value))
                            });
                            done.push(dayShort(day));
                        } catch (err) {
                            failed.push(dayShort(day) + ' (' + err.message + ')');
                        }
                    }
                    if (done.length) {
                        doneBox.textContent = 'Reserved ' + done.length + (done.length === 1 ? ' day' : ' days') + ': '
                            + done.join(', ') + '. Show your plate at the gate during these times.';
                        doneBox.hidden = false;
                    }
                    if (failed.length) {
                        showErrors(form, box, 'Not reserved: ' + failed.join('; '), {});
                    } else {
                        cal.clear();
                    }
                    await loadList();
                    await check();
                });
            });
            return form;
        }

        let checkAvailability = function () { };

        async function loadList() {
            list.replaceChildren(el('h2', { class: 'section' }, 'My reservations'), loading());
            try {
                const items = await Api.get('/api/reservations/me');
                const rows = items.map((r) => [
                    UI.formatRange(r.start, r.end),
                    el('span', {}, badge(label('reservationStatus', r.status), RESERVATION_TONE[r.status]),
                        r.reviewNote ? el('br') : null,
                        r.reviewNote ? el('span', { class: 'soft' }, r.reviewNote) : null),
                    r.cancellable
                        ? el('button', { type: 'button', class: 'btn small quiet', onclick: (e) => cancel(r, e.currentTarget) }, 'Cancel')
                        : (r.status === 'ACTIVE' ? el('span', { class: 'soft' }, 'Too late to cancel') : '')
                ]);
                list.replaceChildren(
                    el('h2', { class: 'section' }, 'My reservations'),
                    table(['When', 'Status', ''], rows, 'No reservations yet.'));
            } catch (err) {
                list.replaceChildren(el('h2', { class: 'section' }, 'My reservations'), notice(err.message, 'error'));
            }
        }

        async function cancel(reservation, button) {
            if (!window.confirm('Cancel the reservation for ' + UI.formatRange(reservation.start, reservation.end) + '?')) return;
            await busy(button, async () => {
                try {
                    await Api.post('/api/reservations/' + reservation.id + '/cancel');
                    await loadList();
                    checkAvailability();
                } catch (err) {
                    window.alert(err.message);
                    await loadList();
                }
            });
        }

        async function loadSpaces() {
            try {
                spacesSlot.replaceChildren(spaceCards(await Api.get('/api/spaces')));
            } catch (err) {
                spacesSlot.replaceChildren(notice(err.message, 'error'));
            }
        }

        function spaceCards(items) {
            if (items.length === 0) return el('p', { class: 'empty' }, 'No parking spaces are set up yet.');
            return el('div', { class: 'bay-grid' }, items.map((t) => {
                const name = (t.area === 'FACULTY' ? label('area', t.area) + ': ' : '') + label('vehicleType', t.vehicleType);
                return el('div', { class: 'bay-card' },
                    UI.ring(t.inside, t.capacity, name),
                    el('strong', { class: 'bay-name' }, name),
                    el('p', { class: 'soft' }, t.available + ' free'));
            }));
        }

        init();
        loadSpaces();
        const timer = setInterval(loadSpaces, 10000); // refreshes only the spaces, not the form
        return () => clearInterval(timer);
    };

    /* =====================================================================
       Log book
       ===================================================================== */
    Pages.logbook = function (me, root) {
        root.append(el('h1', {}, 'Log book'));
        const body = el('div', {}, loading());
        root.append(body);

        Api.get('/api/logs/me?limit=100').then((items) => {
            const rows = items.map((l) => [
                UI.when(l.entryTime),
                l.exitTime ? UI.when(l.exitTime) : badge('Inside now', 'wait'),
                l.identifier || '',
                l.overstay ? badge('Stayed past reservation', 'bad') : ''
            ]);
            body.replaceChildren(table(['Entered', 'Left', 'Plate', 'Note'], rows,
                'No visits yet. Your entries and exits appear here after you drive in.'));
        }).catch((err) => body.replaceChildren(notice(err.message, 'error')));
    };

    /* =====================================================================
       Warnings and penalties
       ===================================================================== */
    Pages.myPenalties = function (me, root) {
        root.append(el('h1', {}, 'Warnings'));
        const body = el('div', {}, loading());
        root.append(body);

        Api.get('/api/users/me').then(async (user) => {
            const items = await Api.get('/api/penalties/me');
            const parts = [];
            if (user.registrationBanned) {
                parts.push(notice('You can no longer reserve parking because of repeated no-shows. Contact the parking office.', 'error'));
            } else if (user.suspendedUntil && new Date(user.suspendedUntil) > UI.now()) {
                parts.push(notice('Your reservations are suspended until ' + UI.formatDateTime(user.suspendedUntil) + '.', 'error'));
            } else if (user.warningCount > 0) {
                parts.push(notice('You have ' + user.warningCount + (user.warningCount === 1 ? ' warning.' : ' warnings.')
                    + ' Reserve only when you will come, and cancel early if your plans change.', 'caution'));
            } else {
                parts.push(notice('No warnings. You are in good standing.'));
            }
            const rows = items.map((p) => [
                UI.when(p.createdAt),
                badge(label('penaltyType', p.type), p.type === 'OVERSTAY' ? 'bad' : 'wait'),
                p.description
            ]);
            parts.push(table(['When', 'Type', 'Details'], rows, 'Nothing here. A warning appears when you do not show up for a reservation.'));
            body.replaceChildren(...parts);
        }).catch((err) => body.replaceChildren(notice(err.message, 'error')));
    };
})();
