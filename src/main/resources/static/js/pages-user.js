/* Pages for regular users: my vehicle, reservations, log book. */
(function () {
    'use strict';

    const { el, notice, badge, loading, table, field, errorBox, clearErrors, showErrors, busy, label } = UI;
    const Pages = (window.Pages = window.Pages || {});

    const STATUS_TONE = { APPROVED: 'good', PENDING: 'wait', REJECTED: 'bad', REVOKED: 'bad' };
    const RESERVATION_TONE = { ACTIVE: 'good', COMPLETED: '', CANCELLED: '', NO_SHOW: 'bad' };

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
    Pages.reservations = function (me, root) {
        root.append(el('h1', {}, 'Reservations'));
        const top = el('div');
        const list = el('div');
        root.append(top, list);
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
            const now = new Date();
            // Default: the next quarter hour, for two hours.
            const start = new Date(now);
            start.setMinutes(Math.ceil((now.getMinutes() + 1) / 15) * 15, 0, 0);
            const end = new Date(start.getTime() + 2 * 60 * 60 * 1000);

            const box = errorBox();
            const doneBox = el('div', { class: 'notice', role: 'status', hidden: true });
            const availability = el('p', { class: 'availability', role: 'status' });

            const form = el('form', { novalidate: true },
                box,
                doneBox,
                el('p', { class: 'soft' }, 'Reserving a space for your ', label('vehicleType', vehicle.vehicleType).toLowerCase(),
                    ' (', vehicle.identifier, ').'),
                field('start', 'Arrive', 'datetime-local', { value: UI.toInputValue(start), attrs: { min: UI.toInputValue(now) } }),
                field('end', 'Leave', 'datetime-local', { value: UI.toInputValue(end) }),
                availability,
                el('button', { type: 'submit', class: 'btn' }, 'Reserve parking'));

            async function check() {
                const f = form.elements;
                if (!f.start.value || !f.end.value) { availability.textContent = ''; return; }
                try {
                    const a = await Api.get('/api/reservations/availability?vehicleType=' + encodeURIComponent(vehicle.vehicleType)
                        + '&start=' + encodeURIComponent(f.start.value) + '&end=' + encodeURIComponent(f.end.value));
                    availability.className = 'availability' + (a.remaining === 0 ? ' bad' : '');
                    availability.textContent = a.remaining === 0
                        ? 'No ' + label('vehicleType', a.vehicleType).toLowerCase() + ' space is left in this time.'
                        : a.remaining + ' of ' + a.capacity + ' ' + label('vehicleType', a.vehicleType).toLowerCase()
                        + ' spaces are free in this time.';
                } catch (err) {
                    availability.className = 'availability bad';
                    availability.textContent = err.message;
                }
            }
            checkAvailability = check;
            form.elements.start.addEventListener('change', check);
            form.elements.end.addEventListener('change', check);

            form.addEventListener('submit', async (event) => {
                event.preventDefault();
                clearErrors(form, box);
                doneBox.hidden = true;
                const f = form.elements;
                const button = form.querySelector('button[type="submit"]');
                await busy(button, async () => {
                    try {
                        await Api.post('/api/reservations', { start: f.start.value, end: f.end.value });
                        doneBox.textContent = 'Reserved. Show your plate at the gate during this time.';
                        doneBox.hidden = false;
                        await loadList();
                        await check();
                    } catch (err) {
                        showErrors(form, box, err.message, err.fieldErrors);
                    }
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
                    badge(label('reservationStatus', r.status), RESERVATION_TONE[r.status]),
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

        init();
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
})();
