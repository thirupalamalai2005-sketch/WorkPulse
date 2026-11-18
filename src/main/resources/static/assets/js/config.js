/**
 * WorkPulse — Frontend Configuration
 *
 * The frontend always talks to the real backend on the SAME origin ("/api"),
 * so session cookies and CSRF work unchanged in development, Docker and production.
 * There is no mock mode: if the API fails, the UI shows an error, never fabricated data.
 */
const CONFIG = Object.freeze({
  APP_NAME: 'WorkPulse',
  API_BASE: '/api',
  PAGE_SIZE: 10,
  TOAST_DURATION: 4000,
  STORAGE_KEY_USER: 'wp_user',
});
