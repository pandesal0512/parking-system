/*
 * Shared by the sign-in page and the app: the light/dark theme and the show-password button.
 * This file is loaded in <head> so the theme is set BEFORE the page is painted (no white flash).
 *
 * The choice (light, dark or system) is kept in this browser's localStorage. It is only a look,
 * never anything secret, so it is fine that it lives in the browser.
 */
(function () {
    'use strict';

    const KEY = 'parking-theme';
    const query = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

    function stored() {
        try {
            const v = window.localStorage.getItem(KEY);
            return v === 'light' || v === 'dark' || v === 'system' ? v : 'system';
        } catch (e) {
            return 'system'; // storage blocked (private window): just follow the system
        }
    }

    function resolve(pref) {
        if (pref === 'system') return query && query.matches ? 'dark' : 'light';
        return pref;
    }

    function apply(pref) {
        document.documentElement.setAttribute('data-theme', resolve(pref));
        document.documentElement.setAttribute('data-theme-pref', pref);
    }

    function set(pref) {
        try { window.localStorage.setItem(KEY, pref); } catch (e) { /* the look still changes for this visit */ }
        apply(pref);
    }

    apply(stored());
    if (query) {
        const onChange = () => { if (stored() === 'system') apply('system'); };
        if (query.addEventListener) query.addEventListener('change', onChange);
    }

    /* ---------- show / hide password ---------- */
    /** Wraps a password input with a Show/Hide button. Returns the wrapper. */
    function passwordWrap(input) {
        const wrap = document.createElement('div');
        wrap.className = 'pw';
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'pw-toggle';
        button.textContent = 'Show';
        button.setAttribute('aria-label', 'Show password');
        button.setAttribute('aria-pressed', 'false');
        button.addEventListener('click', () => {
            const showing = input.type === 'text';
            input.type = showing ? 'password' : 'text';
            button.textContent = showing ? 'Show' : 'Hide';
            button.setAttribute('aria-label', showing ? 'Show password' : 'Hide password');
            button.setAttribute('aria-pressed', showing ? 'false' : 'true');
        });
        if (input.parentNode) input.parentNode.insertBefore(wrap, input);
        wrap.append(input, button);
        return wrap;
    }

    // The sign-in page has its password inputs written in the HTML, so add the buttons there too.
    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('input[type="password"]').forEach((input) => {
            if (!input.closest('.pw')) passwordWrap(input);
        });
    });

    window.Shared = { theme: { get: stored, set, resolve }, passwordWrap };
})();
