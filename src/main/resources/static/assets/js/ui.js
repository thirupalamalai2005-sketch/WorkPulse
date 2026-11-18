/**
 * WorkPulse — shared UI helpers.
 * All dynamic content is built with DOM APIs (textContent), never innerHTML with data,
 * which prevents cross-site scripting.
 */
const Ui = (() => {
  'use strict';

  let tz = undefined; // company time zone, set from /api/schedule

  const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];

  /** Safe element builder: h('td', {class:'x'}, 'text', childNode) */
  function h(tag, attrs, ...children) {
    const el = document.createElement(tag);
    Object.entries(attrs || {}).forEach(([k, v]) => {
      if (v === false || v === null || v === undefined) return;
      if (k === 'class') el.className = v;
      else if (k === 'dataset') Object.assign(el.dataset, v);
      else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2), v);
      else el.setAttribute(k, v === true ? '' : v);
    });
    children.flat().forEach(c => {
      if (c === null || c === undefined || c === false) return;
      el.append(c instanceof Node ? c : document.createTextNode(String(c)));
    });
    return el;
  }

  const setTz = (zone) => { if (zone) tz = zone; };
  const getTz = () => tz;

  /** 'YYYY-MM-DD' -> '05 Mar 2026' (no time-zone shifting for plain dates). */
  function fmtDate(d) {
    if (!d) return '—';
    const [y, m, day] = String(d).split('-').map(Number);
    return `${String(day).padStart(2, '0')} ${MONTHS[m - 1]} ${y}`;
  }
  function fmtTime(iso) {
    if (!iso) return '—';
    return new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: tz })
      .format(new Date(iso));
  }
  function fmtDateTime(iso) {
    if (!iso) return '—';
    return new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit', hour12: false, timeZone: tz }).format(new Date(iso));
  }
  function fmtMinutes(min) {
    if (min === null || min === undefined) return '—';
    return `${Math.floor(min / 60)}h ${String(min % 60).padStart(2, '0')}m`;
  }
  /** Today's date in the company time zone as YYYY-MM-DD. */
  function today() {
    return new Intl.DateTimeFormat('en-CA', { timeZone: tz }).format(new Date());
  }
  const monthStart = () => today().slice(0, 8) + '01';

  const BADGES = {
    PENDING: 'badge-warning', APPROVED: 'badge-success', REJECTED: 'badge-error', CANCELLED: 'badge-neutral',
    PRESENT: 'badge-success', LATE: 'badge-warning', ABSENT: 'badge-error', ON_LEAVE: 'badge-info',
    MISSED_CHECKOUT: 'badge-error', HOLIDAY: 'badge-neutral', WEEKLY_OFF: 'badge-neutral',
    NOT_CHECKED_IN: 'badge-neutral', UPCOMING: 'badge-neutral', ACTIVE: 'badge-success', INACTIVE: 'badge-error',
    ADMIN: 'badge-navy', MANAGER: 'badge-indigo', EMPLOYEE: 'badge-neutral',
  };
  function badge(status, label) {
    return h('span', { class: `badge ${BADGES[status] || 'badge-neutral'}` },
      label || String(status).replace(/_/g, ' ').toLowerCase().replace(/^\w/, c => c.toUpperCase()));
  }

  /** Replaces table body content with a single loading / empty / error row. */
  function tableMessage(tbody, cols, kind, text) {
    const cell = h('td', { colspan: cols, class: 'text-center' },
      kind === 'loading' ? h('span', { class: 'spinner spinner-sm' }) : null,
      kind === 'loading' ? ' ' : null,
      h('span', { class: kind === 'error' ? 'text-error' : 'text-muted' }, text));
    tbody.replaceChildren(h('tr', {}, cell));
  }

  /** Renders pagination controls for a Spring page response. */
  function pager(container, page, onPage) {
    if (!container) return;
    const { number, totalPages, totalElements, size } = normalise(page);
    container.replaceChildren();
    if (totalElements === 0) return;
    const from = number * size + 1, to = Math.min((number + 1) * size, totalElements);
    container.append(h('div', { class: 'pagination-info' }, `Showing ${from}–${to} of ${totalElements}`));
    const controls = h('div', { class: 'pagination-controls' });
    const btn = (label, p, disabled, active) => h('button', {
      type: 'button', class: `page-btn${active ? ' active' : ''}`, disabled, 'aria-label': `Page ${label}`,
      onclick: () => onPage(p),
    }, label);
    controls.append(btn('‹', number - 1, number === 0));
    const start = Math.max(0, Math.min(number - 2, totalPages - 5));
    for (let p = start; p < Math.min(totalPages, start + 5); p++) controls.append(btn(String(p + 1), p, false, p === number));
    controls.append(btn('›', number + 1, number >= totalPages - 1));
    container.append(controls);
  }
  function normalise(p) {
    return { number: p.page ?? 0, totalPages: p.totalPages ?? 0, totalElements: p.totalElements ?? 0, size: p.size ?? 10 };
  }

  /** Accessible confirmation dialog. Resolves true when confirmed. */
  function confirm({ title = 'Are you sure?', message = '', confirmText = 'Confirm', danger = false, withNote = null } = {}) {
    return new Promise(resolve => {
      const note = withNote ? h('textarea', { class: 'form-control', rows: 3, maxlength: 500,
        placeholder: withNote.placeholder || 'Note', 'aria-label': 'Note' }) : null;
      const noteError = h('div', { class: 'form-error' });
      const ok = h('button', { type: 'button', class: `btn ${danger ? 'btn-danger' : 'btn-primary'}` }, confirmText);
      const cancel = h('button', { type: 'button', class: 'btn btn-secondary' }, 'Cancel');
      const backdrop = h('div', { class: 'modal-backdrop open', role: 'dialog', 'aria-modal': 'true', 'aria-label': title },
        h('div', { class: 'modal modal-sm' },
          h('div', { class: 'modal-header' }, h('h3', { class: 'modal-title' }, title)),
          h('div', { class: 'modal-body' }, h('p', { class: 'confirm-message' }, message),
            note ? h('div', { class: 'form-group' }, note, noteError) : null),
          h('div', { class: 'modal-footer' }, cancel, ok)));
      const close = (val) => { document.removeEventListener('keydown', onKey); backdrop.remove(); resolve(val); };
      const onKey = (e) => { if (e.key === 'Escape') close(false); };
      cancel.addEventListener('click', () => close(false));
      ok.addEventListener('click', () => {
        if (withNote?.required && !note.value.trim()) {
          noteError.textContent = 'A note is required.'; noteError.classList.add('visible'); return;
        }
        close(withNote ? { note: note.value.trim() } : true);
      });
      document.addEventListener('keydown', onKey);
      document.body.append(backdrop);
      (note || cancel).focus();
    });
  }

  /** Open/close an existing .modal-backdrop element (Escape closes it too). */
  function openModal(el) {
    el.classList.add('open');
    const first = el.querySelector('input, select, textarea, button');
    if (first) first.focus();
  }
  function closeModal(el) { el.classList.remove('open'); }
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') document.querySelectorAll('.modal-backdrop.open[data-static]').forEach(m => m.classList.remove('open'));
  });

  function fillSelect(select, items, valueKey, labelFn, placeholder) {
    select.replaceChildren();
    if (placeholder !== undefined) select.append(h('option', { value: '' }, placeholder));
    items.forEach(i => select.append(h('option', { value: i[valueKey] }, labelFn(i))));
  }

  function debounce(fn, ms = 300) {
    let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); };
  }

  /** Shows server-side validation messages in a form-level alert element. */
  function showResultError(el, result) {
    if (!el) return;
    const details = result.validationErrors ? Object.values(result.validationErrors).join(' ') : '';
    el.textContent = details || result.error || 'Request failed.';
    el.hidden = false;
  }


  /**
   * Generic modal form. fields: [{name,label,type,options,value,required,hint,maxlength,min,max}]
   * onSubmit(values) must return an Api result ({ok,error,validationErrors}); the dialog closes on ok.
   * Resolves true after a successful submit, false when cancelled.
   */
  function form({ title, fields, submitText = 'Save', onSubmit, intro = null, danger = false }) {
    return new Promise(resolve => {
      const err = h('div', { class: 'alert alert-error', hidden: true, role: 'alert' });
      const inputs = {};
      const body = h('div', { class: 'modal-body' }, intro ? h('p', { class: 'text-muted' }, intro) : null, err);
      fields.forEach(f => {
        const id = `ff-${f.name}`;
        let input;
        if (f.type === 'select') {
          input = h('select', { id, name: f.name, class: 'form-control', required: !!f.required },
            (f.options || []).map(o => h('option', { value: o.value ?? '' }, o.label)));
        } else if (f.type === 'textarea') {
          input = h('textarea', { id, name: f.name, class: 'form-control', rows: 3, maxlength: f.maxlength || 500, required: !!f.required });
        } else if (f.type === 'checkbox') {
          input = h('input', { id, name: f.name, type: 'checkbox' });
        } else {
          input = h('input', { id, name: f.name, class: 'form-control', type: f.type || 'text', maxlength: f.maxlength,
            min: f.min, max: f.max, required: !!f.required, autocomplete: f.type === 'password' ? 'new-password' : 'off' });
        }
        if (f.value !== undefined && f.value !== null) {
          if (f.type === 'checkbox') input.checked = !!f.value; else input.value = f.value;
        }
        inputs[f.name] = input;
        body.append(h('div', { class: f.type === 'checkbox' ? 'form-check' : 'form-group' },
          f.type === 'checkbox' ? [input, h('label', { class: 'form-check-label', for: id }, f.label)]
                                : [h('label', { class: 'form-label', for: id }, f.label + (f.required ? ' *' : '')), input],
          f.hint ? h('div', { class: 'form-hint' }, f.hint) : null));
      });
      const submit = h('button', { type: 'submit', class: `btn ${danger ? 'btn-danger' : 'btn-primary'}` }, submitText);
      const cancel = h('button', { type: 'button', class: 'btn btn-secondary' }, 'Cancel');
      const formEl = h('form', { class: 'modal', novalidate: false },
        h('div', { class: 'modal-header' }, h('h3', { class: 'modal-title' }, title)),
        body, h('div', { class: 'modal-footer' }, cancel, submit));
      const backdrop = h('div', { class: 'modal-backdrop open', role: 'dialog', 'aria-modal': 'true', 'aria-label': title }, formEl);
      const close = (val) => { document.removeEventListener('keydown', onKey); backdrop.remove(); resolve(val); };
      const onKey = (e) => { if (e.key === 'Escape') close(false); };
      cancel.addEventListener('click', () => close(false));
      formEl.addEventListener('submit', async (e) => {
        e.preventDefault();
        err.hidden = true;
        const values = {};
        fields.forEach(f => { values[f.name] = f.type === 'checkbox' ? inputs[f.name].checked : inputs[f.name].value; });
        submit.disabled = true;
        const result = await onSubmit(values);
        submit.disabled = false;
        if (result && result.ok) close(true); else showResultError(err, result || {});
      });
      document.addEventListener('keydown', onKey);
      document.body.append(backdrop);
      const first = body.querySelector('input, select, textarea');
      if (first) first.focus();
    });
  }

  /** Builds tab buttons in `nav`; panels are elements with id "tab-<id>". defs: [{id,label,onShow}] */
  function tabs(nav, defs) {
    const buttons = {};
    const show = (id) => {
      defs.forEach(d => {
        const active = d.id === id;
        document.getElementById('tab-' + d.id).classList.toggle('active', active);
        buttons[d.id].classList.toggle('active', active);
        buttons[d.id].setAttribute('aria-selected', String(active));
      });
      const def = defs.find(d => d.id === id);
      if (def && def.onShow) def.onShow();
    };
    defs.forEach(d => {
      buttons[d.id] = h('button', { type: 'button', class: 'tab-btn', role: 'tab', onclick: () => show(d.id) }, d.label);
      nav.append(buttons[d.id]);
    });
    // hide panels of tabs that were not requested for this role
    document.querySelectorAll('[id^="tab-"]').forEach(p => {
      if (!defs.some(d => 'tab-' + d.id === p.id)) p.remove();
    });
    show(defs[0].id);
    return show;
  }

  return { tabs, form, h, setTz, getTz, fmtDate, fmtTime, fmtDateTime, fmtMinutes, today, monthStart, badge, tableMessage,
           pager, confirm, openModal, closeModal, fillSelect, debounce, showResultError };
})();
