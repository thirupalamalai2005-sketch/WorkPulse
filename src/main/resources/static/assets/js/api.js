/**
 * WorkPulse — API Client (Fetch Wrapper)
 *
 * Provides a unified interface for all HTTP requests:
 * - Session cookies (credentials: 'include')
 * - JSON request / response handling
 * - Centralised HTTP error processing
 * - Automatic 401 → redirect to login
 * - Validation error (400) extraction
 * - Forbidden (403) notification
 */

const Api = (() => {
  'use strict';

  // ── Internal helpers ─────────────────────────────────────────────

  /**
   * Build the full URL for an endpoint path.
   */
  function url(path) {
    return `${CONFIG.API_BASE}${path}`;
  }

  /**
   * Parse the response body safely.
   * Returns { data, error } pair.
   */
  async function parseResponse(response) {
    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
      try {
        const data = await response.json();
        return { data };
      } catch {
        return { data: null };
      }
    }
    const text = await response.text();
    return { data: text ? { message: text } : null };
  }

  /**
   * Core HTTP request method.
   * Returns { ok, status, data, error, validationErrors }
   */
  async function request(endpoint, method = 'GET', body = null, opts = {}) {
    const options = {
      method,
      credentials: 'include',           // preserves JSESSIONID cookie
      headers: {
        'Accept': 'application/json',
        ...opts.headers,
      },
    };

    // Read CSRF token from XSRF-TOKEN cookie if present
    const xsrfCookie = document.cookie.split('; ').find(row => row.startsWith('XSRF-TOKEN='));
    if (xsrfCookie && method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
      const csrfToken = decodeURIComponent(xsrfCookie.split('=')[1]);
      options.headers['X-XSRF-TOKEN'] = csrfToken;
    }

    if (body !== null) {
      options.headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(body);
    }

    let response;
    try {
      response = await fetch(url(endpoint), options);
    } catch (networkError) {
      // Network error — server unreachable
      return {
        ok: false,
        status: 0,
        data: null,
        error: 'Unable to reach the server. Please check your connection.',
        validationErrors: null,
      };
    }

    const { data } = await parseResponse(response);

    // ── 401 Unauthorized: session expired / not logged in ──────────
    if (response.status === 401) {
      // Avoid redirect loop on login page itself
      const onLoginPage = window.location.pathname.includes('login') ||
                          window.location.pathname === '/' ||
                          window.location.pathname === '/index.html';
      if (!onLoginPage) {
        Toast.error('Session Expired', 'Your session has expired. Please log in again.');
        setTimeout(() => {
          Auth.clearSession();
          window.location.href = '/login.html';
        }, 1500);
      }
      return {
        ok: false,
        status: 401,
        data,
        error: data?.message || 'Authentication required.',
        validationErrors: null,
      };
    }

    // ── 403 Forbidden ──────────────────────────────────────────────
    if (response.status === 403) {
      return {
        ok: false,
        status: 403,
        data,
        error: data?.message || 'You do not have permission to perform this action.',
        validationErrors: null,
      };
    }

    // ── 400 Validation errors ──────────────────────────────────────
    if (response.status === 400) {
      return {
        ok: false,
        status: 400,
        data,
        error: data?.message || 'Invalid input. Please check the form.',
        validationErrors: data?.validationErrors || null,
      };
    }

    // ── 409 Conflict (e.g. duplicate email) ────────────────────────
    if (response.status === 409) {
      return {
        ok: false,
        status: 409,
        data,
        error: data?.message || 'A conflict occurred. The resource may already exist.',
        validationErrors: null,
      };
    }

    // ── 5xx Server errors ──────────────────────────────────────────
    if (response.status >= 500) {
      return {
        ok: false,
        status: response.status,
        data,
        error: 'An unexpected server error occurred. Please try again later.',
        validationErrors: null,
      };
    }

    // ── Other non-OK responses ─────────────────────────────────────
    if (!response.ok) {
      return {
        ok: false,
        status: response.status,
        data,
        error: data?.message || `Request failed with status ${response.status}.`,
        validationErrors: null,
      };
    }

    // ── Success ────────────────────────────────────────────────────
    return {
      ok: true,
      status: response.status,
      data,
      error: null,
      validationErrors: null,
    };
  }

  /** Builds "?a=1&b=2" from an object, skipping empty values. */
  function qs(params) {
    const p = new URLSearchParams();
    Object.entries(params || {}).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') p.append(k, v);
    });
    const s = p.toString();
    return s ? `?${s}` : '';
  }

  /** Downloads a file (e.g. CSV) from an authenticated endpoint. Returns { ok, error }. */
  async function download(endpoint, fallbackName) {
    let response;
    try {
      response = await fetch(url(endpoint), { credentials: 'include' });
    } catch {
      return { ok: false, error: 'Unable to reach the server.' };
    }
    if (!response.ok) {
      let msg = `Download failed (${response.status}).`;
      try { msg = (await response.json()).message || msg; } catch { /* keep default */ }
      return { ok: false, error: msg };
    }
    const blob = await response.blob();
    const match = /filename="?([^";]+)"?/.exec(response.headers.get('content-disposition') || '');
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = match ? match[1] : fallbackName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
    return { ok: true };
  }

  // ── Public API ───────────────────────────────────────────────────
  return {
    qs,
    download,
    get:    (endpoint, opts)       => request(endpoint, 'GET',    null, opts),
    post:   (endpoint, body, opts) => request(endpoint, 'POST',   body, opts),
    put:    (endpoint, body, opts) => request(endpoint, 'PUT',    body, opts),
    patch:  (endpoint, body, opts) => request(endpoint, 'PATCH',  body, opts),
    delete: (endpoint, opts)       => request(endpoint, 'DELETE', null, opts),
  };
})();

/* ════════════════════════════════════════════════════════════════
   TOAST NOTIFICATION UTILITY
   ════════════════════════════════════════════════════════════════ */
const Toast = (() => {
  'use strict';

  // Icons
  const ICONS = {
    success: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>`,
    error:   `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>`,
    warning: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M12 9v4m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/></svg>`,
    info:    `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>`,
  };

  function getContainer() {
    let c = document.getElementById('toast-container');
    if (!c) {
      c = document.createElement('div');
      c.id = 'toast-container';
      c.className = 'toast-container';
      document.body.appendChild(c);
    }
    return c;
  }

  function show(type, title, message = '', duration = CONFIG.TOAST_DURATION) {
    const container = getContainer();
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerHTML = `
      <div class="toast-icon">${ICONS[type]}</div>
      <div class="toast-content">
        <div class="toast-title">${escapeHtml(title)}</div>
        ${message ? `<div class="toast-message">${escapeHtml(String(message))}</div>` : ''}
      </div>
      <button class="toast-close" aria-label="Dismiss">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
      </button>
      <div class="toast-progress" style="animation-duration: ${duration}ms"></div>
    `;

    const closeBtn = toast.querySelector('.toast-close');
    const dismiss = () => {
      toast.classList.add('hiding');
      toast.addEventListener('animationend', () => toast.remove(), { once: true });
    };

    closeBtn.addEventListener('click', dismiss);
    const timer = setTimeout(dismiss, duration);
    toast.addEventListener('mouseenter', () => clearTimeout(timer));

    container.appendChild(toast);
  }

  return {
    success: (title, msg, dur) => show('success', title, msg, dur),
    error:   (title, msg, dur) => show('error',   title, msg, dur),
    warning: (title, msg, dur) => show('warning', title, msg, dur),
    info:    (title, msg, dur) => show('info',    title, msg, dur),
  };
})();

/* ════════════════════════════════════════════════════════════════
   HTML ESCAPE UTILITY (prevent XSS)
   ════════════════════════════════════════════════════════════════ */
function escapeHtml(str) {
  if (str === null || str === undefined) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
