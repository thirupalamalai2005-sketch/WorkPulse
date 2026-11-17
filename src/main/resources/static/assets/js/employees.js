/** WorkPulse — Employees: directory for everyone, account management for administrators. */
(async () => {
  'use strict';
  const user = await Auth.requireAuth();
  if (!user) return;
  await Layout.init('employees', 'Employees', user);
  const { h } = Ui;
  const admin = Auth.isAdmin(user);

  const body = document.getElementById('emp-body');
  const pagerEl = document.getElementById('emp-pager');
  const errorBox = document.getElementById('page-error');
  const q = document.getElementById('f-q'), dept = document.getElementById('f-dept'),
        status = document.getElementById('f-status'), sort = document.getElementById('f-sort'),
        dirBtn = document.getElementById('btn-dir');
  let dir = 'asc', page = 0, departments = [], managers = [];

  document.querySelector('thead tr').lastElementChild.hidden = !admin; // Actions column
  if (admin) { status.hidden = false; document.getElementById('btn-add').hidden = false; }

  const deptRes = await Api.get('/departments');
  if (deptRes.ok) departments = deptRes.data;
  Ui.fillSelect(dept, departments, 'id', d => d.name, 'All departments');

  async function load() {
    Ui.tableMessage(body, admin ? 7 : 6, 'loading', 'Loading employees…');
    errorBox.hidden = true;
    const r = await Api.get('/employees' + Api.qs({ q: q.value.trim(), departmentId: dept.value, status: status.value,
      sort: sort.value, dir, page, size: CONFIG.PAGE_SIZE }));
    if (!r.ok) { Ui.tableMessage(body, admin ? 7 : 6, 'error', r.error); pagerEl.replaceChildren(); return; }
    if (r.data.content.length === 0) { Ui.tableMessage(body, admin ? 7 : 6, 'empty', 'No employees match your filters.'); pagerEl.replaceChildren(); return; }
    body.replaceChildren(...r.data.content.map(row));
    Ui.pager(pagerEl, r.data, (p) => { page = p; load(); });
  }

  function row(e) {
    const tr = h('tr', {},
      h('td', {}, h('span', { class: 'employee-id' }, e.employeeCode)),
      h('td', {}, h('div', { class: 'employee-name-cell' }, h('div', {}, h('strong', {}, e.name),
        h('div', { class: 'text-xs text-muted' }, e.email), e.phone ? h('div', { class: 'text-xs text-muted' }, e.phone) : null))),
      h('td', {}, e.departmentName || '—'),
      h('td', {}, e.designation || '—'),
      h('td', {}, e.managerName || '—'),
      h('td', {}, Ui.badge(e.status), e.role ? [' ', Ui.badge(e.role)] : null));
    if (admin) tr.append(h('td', {}, actions(e)));
    return tr;
  }

  function actions(e) {
    const isSelf = e.userId === user.userId;
    const btn = (label, fn, cls = 'btn-secondary', tip) => h('button', { type: 'button', class: `btn btn-xs ${cls}`, title: tip, onclick: fn }, label);
    return h('div', { style: 'display:flex;gap:4px;flex-wrap:wrap' },
      btn('Edit', () => editEmployee(e)),
      btn('Role', () => changeRole(e)),
      btn('Password', () => resetPassword(e), 'btn-secondary', 'Reset this person\'s password'),
      isSelf ? null : (e.status === 'ACTIVE'
        ? btn('Deactivate', () => toggle(e, false), 'btn-danger-outline')
        : btn('Activate', () => toggle(e, true), 'btn-success')));
  }

  async function loadManagers() {
    // eligible managers = active MANAGER/ADMIN accounts (admin sees roles)
    const r = await Api.get('/employees' + Api.qs({ status: 'ACTIVE', size: 100, sort: 'name' }));
    managers = r.ok ? r.data.content.filter(x => x.role === 'MANAGER' || x.role === 'ADMIN') : [];
  }
  const deptOptions = () => [{ value: '', label: '— none —' }, ...departments.map(d => ({ value: d.id, label: d.name }))];
  const mgrOptions = (excludeId) => [{ value: '', label: '— none —' }, ...managers.filter(m => m.id !== excludeId).map(m => ({ value: m.id, label: `${m.name} (${m.role})` }))];
  const num = (v) => v === '' ? null : Number(v);

  async function addEmployee() {
    await loadManagers();
    const ok = await Ui.form({
      title: 'Add employee', submitText: 'Create account',
      fields: [
        { name: 'username', label: 'Full name', required: true, maxlength: 50 },
        { name: 'email', label: 'Work email', type: 'email', required: true, maxlength: 150 },
        { name: 'password', label: 'Initial password', type: 'password', required: true, hint: '12+ characters, upper/lower case, digit and symbol.' },
        { name: 'role', label: 'Role', type: 'select', required: true, value: 'EMPLOYEE', options: ['EMPLOYEE', 'MANAGER', 'ADMIN'].map(r => ({ value: r, label: r })) },
        { name: 'departmentId', label: 'Department', type: 'select', options: deptOptions() },
        { name: 'managerId', label: 'Manager', type: 'select', options: mgrOptions() },
        { name: 'designation', label: 'Designation', maxlength: 100 },
        { name: 'phone', label: 'Phone', type: 'tel', maxlength: 20 },
        { name: 'joinDate', label: 'Join date', type: 'date', value: Ui.today() },
      ],
      onSubmit: (v) => Api.post('/admin/users', { ...v, departmentId: num(v.departmentId), managerId: num(v.managerId), joinDate: v.joinDate || null }),
    });
    if (ok) { Toast.success('Employee created', 'The account is ready to sign in.'); load(); }
  }

  async function editEmployee(e) {
    await loadManagers();
    const ok = await Ui.form({
      title: `Edit ${e.name}`,
      fields: [
        { name: 'username', label: 'Full name', required: true, value: e.name, maxlength: 50 },
        { name: 'departmentId', label: 'Department', type: 'select', value: e.departmentId ?? '', options: deptOptions() },
        { name: 'managerId', label: 'Manager', type: 'select', value: e.managerId ?? '', options: mgrOptions(e.id) },
        { name: 'designation', label: 'Designation', value: e.designation || '', maxlength: 100 },
        { name: 'phone', label: 'Phone', type: 'tel', value: e.phone || '', maxlength: 20 },
        { name: 'joinDate', label: 'Join date', type: 'date', required: true, value: e.joinDate },
      ],
      onSubmit: (v) => Api.put(`/employees/${e.id}`, { ...v, departmentId: num(v.departmentId), managerId: num(v.managerId) }),
    });
    if (ok) { Toast.success('Saved', 'Employee updated.'); load(); }
  }

  async function changeRole(e) {
    const ok = await Ui.form({
      title: `Change role — ${e.name}`, submitText: 'Change role',
      intro: 'The person will be signed out and must sign in again for the new role to apply.',
      fields: [{ name: 'role', label: 'Role', type: 'select', required: true, value: e.role,
        options: ['EMPLOYEE', 'MANAGER', 'ADMIN'].map(r => ({ value: r, label: r })) }],
      onSubmit: (v) => Api.patch(`/employees/${e.id}/role`, { role: v.role }),
    });
    if (ok) { Toast.success('Role updated'); load(); }
  }

  async function resetPassword(e) {
    const ok = await Ui.form({
      title: `Reset password — ${e.name}`, submitText: 'Reset password', danger: true,
      intro: 'Sets a new password and ends all of this person’s sessions. Share it through a secure channel.',
      fields: [{ name: 'newPassword', label: 'New password', type: 'password', required: true }],
      onSubmit: (v) => Api.post(`/admin/users/${e.userId}/reset-password`, { newPassword: v.newPassword }),
    });
    if (ok) Toast.success('Password reset', 'Existing sessions were ended.');
  }

  async function toggle(e, active) {
    const yes = await Ui.confirm({
      title: active ? 'Activate account?' : 'Deactivate account?', danger: !active,
      message: active ? `${e.name} will be able to sign in again.` : `${e.name} will be signed out and unable to sign in. Records are kept.`,
      confirmText: active ? 'Activate' : 'Deactivate',
    });
    if (!yes) return;
    const r = await Api.patch(`/employees/${e.id}/status`, { active });
    if (r.ok) { Toast.success(active ? 'Activated' : 'Deactivated'); load(); } else Toast.error('Not possible', r.error);
  }

  const reload = () => { page = 0; load(); };
  q.addEventListener('input', Ui.debounce(reload));
  [dept, status, sort].forEach(el => el.addEventListener('change', reload));
  dirBtn.addEventListener('click', () => { dir = dir === 'asc' ? 'desc' : 'asc'; dirBtn.textContent = dir === 'asc' ? 'A→Z' : 'Z→A'; reload(); });
  document.getElementById('btn-add').addEventListener('click', addEmployee);
  load();
})();
