/*
 * Api: the one place that talks to the backend.
 * Every page uses this, so token handling and error handling live here only.
 *
 * The token is kept in sessionStorage: it is forgotten when the browser tab closes,
 * which is the safer choice on shared school computers.
 */
(function () {
    'use strict';

    const TOKEN_KEY = 'parking.token';

    class ApiError extends Error {
        constructor(status, code, message, fieldErrors) {
            super(message);
            this.status = status;
            this.code = code;
            this.fieldErrors = fieldErrors || {};
        }
    }

    function getToken() {
        try { return sessionStorage.getItem(TOKEN_KEY); } catch (e) { return null; }
    }

    function setToken(token) {
        try { sessionStorage.setItem(TOKEN_KEY, token); } catch (e) { /* storage blocked */ }
    }

    function clearToken() {
        try { sessionStorage.removeItem(TOKEN_KEY); } catch (e) { /* storage blocked */ }
    }

    async function request(method, path, body) {
        const headers = {};
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        const token = getToken();
        if (token) headers['Authorization'] = 'Bearer ' + token;

        let response;
        try {
            response = await fetch(path, {
                method,
                headers,
                body: body !== undefined ? JSON.stringify(body) : undefined
            });
        } catch (e) {
            throw new ApiError(0, 'NETWORK', 'Cannot reach the server. Check that the backend is running.');
        }

        let data = null;
        const text = await response.text();
        if (text) {
            try { data = JSON.parse(text); } catch (e) { /* not JSON */ }
        }

        if (!response.ok) {
            // A token that is expired or no longer valid: go back to the login page.
            if (response.status === 401 && token && data && data.code === 'UNAUTHORIZED') {
                clearToken();
                window.location.replace('index.html?expired=1');
            }
            throw new ApiError(
                response.status,
                data && data.code,
                (data && data.message) || 'The request failed (' + response.status + ').',
                data && data.fieldErrors
            );
        }
        return data;
    }

    window.Api = {
        ApiError,
        getToken,
        setToken,
        clearToken,
        get: (path) => request('GET', path),
        post: (path, body) => request('POST', path, body === undefined ? {} : body),
        put: (path, body) => request('PUT', path, body),
        del: (path) => request('DELETE', path)
    };
})();
