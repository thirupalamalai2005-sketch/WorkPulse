/**
 * WorkPulse — Login Controller
 *
 * Handles login form submission, validation, show/hide password,
 * and the optional registration link.
 */

document.addEventListener('DOMContentLoaded', async () => {
  'use strict';

  // 1. If user is already authenticated, redirect to dashboard immediately
  await Auth.redirectIfAuthenticated();

  // 2. DOM Elements
  const form = document.getElementById('login-form');
  const emailInput = document.getElementById('email');
  const passwordInput = document.getElementById('password');
  const loginBtn = document.getElementById('login-btn');
  const togglePasswordBtn = document.getElementById('toggle-password');
  const eyeIcon = document.getElementById('eye-icon');
  const eyeOffIcon = document.getElementById('eye-off-icon');
  const errorAlert = document.getElementById('login-error');
  const errorAlertText = document.getElementById('login-error-text');

  // 3. Show / Hide Password Toggle
  if (togglePasswordBtn) {
    togglePasswordBtn.addEventListener('click', () => {
      const isPassword = passwordInput.getAttribute('type') === 'password';
      passwordInput.setAttribute('type', isPassword ? 'text' : 'password');
      togglePasswordBtn.setAttribute('aria-label', isPassword ? 'Hide password' : 'Show password');
      eyeIcon.style.display = isPassword ? 'none' : 'block';
      eyeOffIcon.style.display = isPassword ? 'block' : 'none';
    });
  }

  // 4. Show the "create account" link only when the server allows self-registration
  try {
    const cfg = await Api.get('/auth/config');
    if (cfg.ok && cfg.data.registrationEnabled) document.getElementById('register-link-row').hidden = false;
  } catch { /* link stays hidden */ }

  // Clear errors on field typing
  emailInput.addEventListener('input', () => {
    Validators.clearFieldError('email');
    hideAlert();
  });
  passwordInput.addEventListener('input', () => {
    Validators.clearFieldError('password');
    hideAlert();
  });

  function showAlert(msg) {
    if (errorAlert && errorAlertText) {
      errorAlertText.textContent = msg;
      errorAlert.style.display = 'flex';
    }
  }

  function hideAlert() {
    if (errorAlert) {
      errorAlert.style.display = 'none';
    }
  }

  // 5. Form Submission
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    hideAlert();
    Validators.clearFormErrors('login-form');

    const email = emailInput.value.trim();
    const password = passwordInput.value;

    // Validation rules
    const rules = {
      email: [
        { type: 'required', message: 'Email address is required.' },
        { type: 'email', message: 'Please enter a valid email address.' }
      ],
      password: [
        { type: 'required', message: 'Password is required.' }
      ]
    };

    const errors = Validators.validate({ email, password }, rules);
    if (!Validators.isValid(errors)) {
      Validators.applyErrors(errors);
      return;
    }

    // Set loading state
    loginBtn.classList.add('loading');
    loginBtn.disabled = true;

    try {
      const response = await Auth.login(email, password);

      if (response.ok) {
        Toast.success('Login Successful', 'Redirecting to your dashboard...');
        setTimeout(() => {
          window.location.href = '/dashboard.html';
        }, 800);
      } else {
        showAlert(response.error || 'Authentication failed. Please check your credentials.');
        loginBtn.classList.remove('loading');
        loginBtn.disabled = false;
      }
    } catch (err) {
      showAlert('A network error occurred. Please try again.');
      loginBtn.classList.remove('loading');
      loginBtn.disabled = false;
    }
  });
});
