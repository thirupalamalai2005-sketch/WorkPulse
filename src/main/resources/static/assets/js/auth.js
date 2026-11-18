/**
 * WorkPulse — Authentication Utilities
 *
 * Login, logout, session verification and role helpers.
 * Passwords are never stored client-side. Role checks here only decide what the UI shows;
 * the server enforces every permission independently.
 */
const Auth = (() => {
  'use strict';

  let currentUser = null;

  async function login(email, password) {
    const result = await Api.post('/auth/login', { email, password });
    if (result.ok) currentUser = null; // force re-fetch of the full profile
    return result;
  }

  async function logout() {
    try {
      await Api.post('/auth/logout', null);
    } finally {
      currentUser = null;
      window.location.href = '/login.html';
    }
  }

  /** Returns the authenticated user (from the server) or null. */
  async function fetchCurrentUser() {
    if (currentUser) return currentUser;
    const result = await Api.get('/auth/me');
    if (result.ok && result.data) {
      currentUser = result.data;
      return currentUser;
    }
    return null;
  }

  /** Route guard: call first on every protected page. */
  async function requireAuth() {
    const user = await fetchCurrentUser();
    if (!user) {
      window.location.href = '/login.html';
      return null;
    }
    return user;
  }

  /** Requires one of the given roles, otherwise shows a message and returns to the dashboard. */
  async function requireRole(...roles) {
    const user = await requireAuth();
    if (!user) return null;
    if (!roles.includes(user.role)) {
      Toast.warning('Access Denied', 'You do not have permission to view this page.');
      setTimeout(() => { window.location.href = '/dashboard.html'; }, 1500);
      return null;
    }
    return user;
  }

  const requireAdmin = () => requireRole('ADMIN');

  async function redirectIfAuthenticated() {
    const user = await fetchCurrentUser();
    if (user) window.location.href = '/dashboard.html';
  }

  const isAdmin = (u) => u?.role === 'ADMIN';
  const isReviewer = (u) => u?.role === 'ADMIN' || u?.role === 'MANAGER';

  function getInitials(name) {
    if (!name) return '?';
    return name.trim().split(/\s+/).map(w => w[0]).join('').toUpperCase().slice(0, 2);
  }

  return { login, logout, fetchCurrentUser, requireAuth, requireRole, requireAdmin,
           redirectIfAuthenticated, isAdmin, isReviewer, getInitials };
})();
