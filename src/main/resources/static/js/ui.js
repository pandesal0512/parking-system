/*
 * UI: small helpers shared by every page.
 *
 * SECURITY NOTE: el() puts text on the page with append(), which never interprets HTML.
 * Do not use innerHTML with anything that came from the server or from a user.
 */
(function () {
    'use strict';

    /* ---------- build elements (text only, safe) ---------- */
    function el(tag, attrs, ...children) {
        const node = document.createElement(tag);
        Object.entries(attrs || {}).forEach(([key, value]) => {
            if (value === null || value === undefined || value === false) return;
            if (key === 'class') node.className = value;
            else if (key.startsWith('on') && typeof value === 'function') node.addEventListener(key.slice(2), value);
            else node.setAttribute(key, value === true ? '' : value);
        });
        children.flat().forEach((child) => {
            if (child === null || child === undefined || child === false) return;
            node.append(child);
        });
        return node;
    }

    /* ---------- dates ---------- */
    const dateTimeFmt = new Intl.DateTimeFormat('en-PH', { dateStyle: 'medium', timeStyle: 'short' });
    const dateFmt = new Intl.DateTimeFormat('en-PH', { dateStyle: 'medium' });
    const timeFmt = new Intl.DateTimeFormat('en-PH', { timeStyle: 'short' });

    function parse(value) {
        if (!value) return null;
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? null : date;
    }
    function formatDateTime(value) { const d = parse(value); return d ? dateTimeFmt.format(d) : ''; }
    function formatDate(value) { const d = parse(value); return d ? dateFmt.format(d) : ''; }
    function formatTime(value) { const d = parse(value); return d ? timeFmt.format(d) : ''; }
    /** A date and time that stays on one line inside a table cell. */
    function when(value) { return el('span', { class: 'nowrap' }, formatDateTime(value)); }

    /** "Nov 18, 2026, 8:00 AM to 10:00 AM" (adds the date again if it ends on another day). */
    function formatRange(start, end) {
        const s = parse(start);
        const e = parse(end);
        if (!s || !e) return '';
        const sameDay = s.toDateString() === e.toDateString();
        return dateTimeFmt.format(s) + ' to ' + (sameDay ? timeFmt.format(e) : dateTimeFmt.format(e));
    }

    function pad(n) { return String(n).padStart(2, '0'); }
    /** Value for <input type="datetime-local"> */
    function toInputValue(d) {
        return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
            + 'T' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    }
    /** Value for <input type="date"> */
    function toDateInputValue(d) {
        return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
    }

    /* ---------- words for codes the server sends ---------- */
    const LABELS = {
        vehicleType: { MOTORCYCLE: 'Motorcycle', CAR: 'Car', TRICYCLE: 'Tricycle / e-trike' },
        vehicleStatus: { PENDING: 'Waiting for approval', APPROVED: 'Approved', REJECTED: 'Rejected', REVOKED: 'Revoked' },
        reservationStatus: { ACTIVE: 'Active', COMPLETED: 'Completed', CANCELLED: 'Cancelled', NO_SHOW: 'No show' },
        userType: { STUDENT: 'Student', FACULTY: 'Faculty member', DEAN: 'Dean', PERSONNEL: 'Personnel' },
        role: { USER: 'User', PARKING_MANAGER: 'Parking manager', SUPER_ADMIN: 'Super admin' },
        deny: {
            UNREGISTERED: 'Plate not registered',
            VEHICLE_NOT_APPROVED: 'Vehicle not approved',
            ACCOUNT_DISABLED: 'Account deactivated',
            ACCOUNT_SUSPENDED: 'Owner suspended',
            ALREADY_INSIDE: 'Already inside',
            NO_RESERVATION: 'No valid reservation',
            LOT_FULL: 'No space left'
        }
    };
    function label(group, code) { return (LABELS[group] && LABELS[group][code]) || code || ''; }

    function typeOptions(includeBlank) {
        const options = Object.entries(LABELS.vehicleType).map(([value, text]) => ({ value, label: text }));
        return includeBlank ? [{ value: '', label: 'Choose one' }, ...options] : options;
    }

    /* ---------- small components ---------- */
    function notice(text, kind) {
        return el('div', { class: 'notice' + (kind ? ' ' + kind : ''), role: kind === 'error' ? 'alert' : 'status' }, text);
    }

    /** A status pill. It always carries a word, so colour is never the only signal. */
    function badge(text, tone) {
        return el('span', { class: 'badge' + (tone ? ' ' + tone : '') }, text);
    }

    function loading() {
        return el('p', { class: 'empty' }, 'Loading...');
    }

    /** cells can be strings or DOM nodes. Shows emptyText instead of an empty table. */
    function table(headers, rows, emptyText) {
        if (!rows.length) return el('p', { class: 'empty' }, emptyText);
        return el('div', { class: 'table-wrap' },
            el('table', {},
                el('thead', {}, el('tr', {}, headers.map((h) => el('th', { scope: 'col' }, h)))),
                el('tbody', {}, rows.map((cells) => el('tr', {}, cells.map((c) => el('td', {}, c)))))));
    }

    /**
     * A form field. type 'select' takes opts.options [{value,label}].
     * opts: value, hint, required (default true), autocomplete, attrs (extra attributes).
     */
    function field(id, labelText, type, opts) {
        const o = opts || {};
        const required = o.required !== false;
        let control;
        if (type === 'select') {
            control = el('select', { id, name: id, required },
                (o.options || []).map((opt) => el('option', { value: opt.value }, opt.label)));
        } else {
            control = el('input', Object.assign({ id, name: id, type, autocomplete: o.autocomplete, required }, o.attrs || {}));
        }
        if (o.value !== undefined && o.value !== null) control.value = o.value;

        return el('div', { class: 'field' },
            el('label', { for: id }, labelText),
            control,
            o.hint ? el('span', { class: 'hint' }, o.hint) : null,
            el('span', { class: 'field-error', 'data-error-for': id }));
    }

    function errorBox() {
        return el('div', { class: 'notice error form-error', role: 'alert', hidden: true });
    }

    function clearErrors(form, box) {
        form.querySelectorAll('.field-error').forEach((s) => { s.textContent = ''; });
        form.querySelectorAll('[aria-invalid]').forEach((i) => i.removeAttribute('aria-invalid'));
        box.textContent = '';
        box.hidden = true;
    }

    /** Shows the message, and any per-field messages next to their fields. */
    function showErrors(form, box, message, fieldErrors) {
        box.textContent = message;
        box.hidden = false;
        Object.entries(fieldErrors || {}).forEach(([name, text]) => {
            const slot = form.querySelector('[data-error-for="' + name + '"]');
            if (slot) slot.textContent = text;
            const input = form.elements[name];
            if (input) input.setAttribute('aria-invalid', 'true');
        });
    }

    /** Disables a button while an async action runs, so it can't be double-submitted. */
    async function busy(button, action) {
        button.disabled = true;
        try {
            return await action();
        } finally {
            button.disabled = false;
        }
    }

    window.UI = {
        el, formatDateTime, formatDate, formatTime, formatRange, when, toInputValue, toDateInputValue,
        label, typeOptions, notice, badge, loading, table, field, errorBox, clearErrors, showErrors, busy
    };
})();
