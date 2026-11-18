/** WorkPulse — public registration (only works when the server enables self-registration). */
document.addEventListener('DOMContentLoaded', async () => {
  'use strict';
  const form = document.getElementById('register-form');
  const errorBox = document.getElementById('register-error');
  const btn = document.getElementById('register-btn');

  const cfg = await Api.get('/auth/config');
  if (cfg.ok && !cfg.data.registrationEnabled) {
    errorBox.textContent = 'Self-registration is disabled. Please ask an administrator to create your account.';
    errorBox.hidden = false;
    btn.disabled = true;
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    errorBox.hidden = true;
    const username = document.getElementById('username').value.trim();
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value;
    if (password !== document.getElementById('confirm').value) {
      errorBox.textContent = 'Passwords do not match.';
      errorBox.hidden = false;
      return;
    }
    btn.disabled = true;
    const result = await Api.post('/auth/register', { username, email, password });
    btn.disabled = false;
    if (result.ok) {
      Toast.success('Account created', 'You can now sign in.');
      setTimeout(() => { window.location.href = '/login.html'; }, 1200);
    } else {
      Ui.showResultError(errorBox, result);
    }
  });
});
