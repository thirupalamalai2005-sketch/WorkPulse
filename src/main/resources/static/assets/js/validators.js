/**
 * WorkPulse — Form Validation Utilities
 *
 * All validation functions are pure (no side effects).
 * DOM helpers show/clear field errors.
 */

const Validators = (() => {
  'use strict';

  /* ── Pure Validators ─────────────────────────────────────────── */

  function isRequired(value) {
    return value !== null && value !== undefined && String(value).trim().length > 0;
  }

  function isEmail(value) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(value).trim());
  }

  function isMinLength(value, min) {
    return String(value).trim().length >= min;
  }

  function isMaxLength(value, max) {
    return String(value).trim().length <= max;
  }

  function isPhone(value) {
    return /^[+]?[\d\s\-()]{7,15}$/.test(String(value).trim());
  }

  function isDate(value) {
    if (!value) return false;
    const d = new Date(value);
    return !isNaN(d.getTime());
  }

  function isDateRange(from, to) {
    return new Date(from) <= new Date(to);
  }

  function isStrongPassword(value) {
    if (!value) return false;
    // Cannot have leading or trailing whitespace
    if (value !== value.trim()) return false;
    // Length 12-128
    if (value.length < 12 || value.length > 128) return false;
    // Must contain uppercase, lowercase, digit, and special char
    const hasUpper = /[A-Z]/.test(value);
    const hasLower = /[a-z]/.test(value);
    const hasDigit = /[0-9]/.test(value);
    const hasSpecial = /[^A-Za-z0-9\s]/.test(value);
    return hasUpper && hasLower && hasDigit && hasSpecial;
  }

  /* ── Rule-based validation ───────────────────────────────────── */
  /**
   * Validate a form using a rules object.
   *
   * @param {Object} data    - { fieldName: value }
   * @param {Object} rules   - { fieldName: [ {type, param, message} ] }
   * @returns {Object} errors - { fieldName: 'error message' | null }
   */
  function validate(data, rules) {
    const errors = {};

    for (const [field, fieldRules] of Object.entries(rules)) {
      const value = data[field];
      let fieldError = null;

      for (const rule of fieldRules) {
        let passed = true;

        switch (rule.type) {
          case 'required':       passed = isRequired(value); break;
          case 'email':          passed = !value || isEmail(value); break;
          case 'minLength':      passed = !value || isMinLength(value, rule.param); break;
          case 'maxLength':      passed = !value || isMaxLength(value, rule.param); break;
          case 'phone':          passed = !value || isPhone(value); break;
          case 'date':           passed = !value || isDate(value); break;
          case 'strongPassword': passed = !value || isStrongPassword(value); break;
          case 'custom':         passed = rule.fn(value, data); break;
        }

        if (!passed) {
          fieldError = rule.message;
          break; // stop at first failing rule per field
        }
      }

      errors[field] = fieldError;
    }

    return errors;
  }

  /** Returns true if the errors object has no non-null values. */
  function isValid(errors) {
    return Object.values(errors).every(e => e === null);
  }

  /* ── DOM Helpers ─────────────────────────────────────────────── */

  /**
   * Show an error message under an input element.
   * Looks for a sibling <p class="form-error"> element.
   */
  function showFieldError(fieldId, message) {
    const field = document.getElementById(fieldId);
    if (!field) return;
    field.classList.add('error');

    // Find or create error element
    let errorEl = field.closest('.form-group')?.querySelector('.form-error');
    if (!errorEl) {
      errorEl = field.parentElement?.querySelector('.form-error');
    }
    if (errorEl) {
      errorEl.textContent = message;
      errorEl.classList.add('visible');
    }
  }

  /** Clear error state from a field. */
  function clearFieldError(fieldId) {
    const field = document.getElementById(fieldId);
    if (!field) return;
    field.classList.remove('error');

    const errorEl = field.closest('.form-group')?.querySelector('.form-error');
    if (errorEl) {
      errorEl.textContent = '';
      errorEl.classList.remove('visible');
    }
  }

  /** Clear all errors in a form. */
  function clearFormErrors(formId) {
    const form = document.getElementById(formId);
    if (!form) return;
    form.querySelectorAll('.form-control').forEach(el => el.classList.remove('error'));
    form.querySelectorAll('.form-error').forEach(el => {
      el.textContent = '';
      el.classList.remove('visible');
    });
  }

  /**
   * Apply a validation errors object to the form DOM.
   * Keys in errors must match input IDs.
   */
  function applyErrors(errors) {
    for (const [field, message] of Object.entries(errors)) {
      if (message) {
        showFieldError(field, message);
      } else {
        clearFieldError(field);
      }
    }
  }

  /**
   * Apply server-side validation errors (validationErrors map from API).
   */
  function applyServerErrors(validationErrors) {
    if (!validationErrors) return;
    for (const [field, message] of Object.entries(validationErrors)) {
      showFieldError(field, message);
    }
  }

  /* ── Public Interface ────────────────────────────────────────── */
  return {
    isRequired,
    isEmail,
    isMinLength,
    isMaxLength,
    isPhone,
    isDate,
    isDateRange,
    isStrongPassword,
    validate,
    isValid,
    showFieldError,
    clearFieldError,
    clearFormErrors,
    applyErrors,
    applyServerErrors,
  };
})();
