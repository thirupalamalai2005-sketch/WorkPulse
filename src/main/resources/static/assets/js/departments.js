/** WorkPulse — Department management (administrators). */
(async () => {
  'use strict';
  const user = await Auth.requireAdmin();
  if (!user) return;
  await Layout.init('departments', 'Departments', user);
  const { h } = Ui;
  const body = document.getElementById('dept-body');
  let departments = [];

  async function load() {
    Ui.tableMessage(body, 4, 'loading', 'Loading departments…');
    const r = await Api.get('/departments');
    if (!r.ok) { Ui.tableMessage(body, 4, 'error', r.error); return; }
    departments = r.data;
    if (departments.length === 0) { Ui.tableMessage(body, 4, 'empty', 'No departments yet. Add the first one.'); return; }
    body.replaceChildren(...departments.map(d => h('tr', {},
      h('td', {}, h('strong', {}, d.name)), h('td', {}, d.description || '—'), h('td', {}, String(d.employeeCount)),
      h('td', {}, h('div', { style: 'display:flex;gap:4px' },
        h('button', { type: 'button', class: 'btn btn-xs btn-secondary', onclick: () => edit(d) }, 'Edit'),
        h('button', { type: 'button', class: 'btn btn-xs btn-danger-outline', onclick: () => remove(d) }, 'Delete'))))));
  }

  const fields = (d) => [
    { name: 'name', label: 'Name', required: true, maxlength: 100, value: d?.name || '' },
    { name: 'description', label: 'Description', type: 'textarea', maxlength: 255, value: d?.description || '' },
  ];

  async function add() {
    const ok = await Ui.form({ title: 'Add department', fields: fields(), submitText: 'Create',
      onSubmit: (v) => Api.post('/departments', v) });
    if (ok) { Toast.success('Department created'); load(); }
  }
  async function edit(d) {
    const ok = await Ui.form({ title: `Edit ${d.name}`, fields: fields(d),
      onSubmit: (v) => Api.put(`/departments/${d.id}`, v) });
    if (ok) { Toast.success('Department updated'); load(); }
  }
  async function remove(d) {
    if (d.employeeCount === 0) {
      const yes = await Ui.confirm({ title: 'Delete department?', message: `“${d.name}” will be permanently removed.`, confirmText: 'Delete', danger: true });
      if (!yes) return;
      const r = await Api.delete(`/departments/${d.id}`);
      if (r.ok) { Toast.success('Department deleted'); load(); } else Toast.error('Not possible', r.error);
      return;
    }
    const others = departments.filter(x => x.id !== d.id);
    if (others.length === 0) { Toast.warning('Cannot delete', 'Create another department to move the employees to first.'); return; }
    const ok = await Ui.form({
      title: `Delete ${d.name}`, submitText: 'Move employees and delete', danger: true,
      intro: `${d.employeeCount} employee(s) belong to this department. Choose where they should go.`,
      fields: [{ name: 'to', label: 'Move employees to', type: 'select', required: true, options: others.map(o => ({ value: o.id, label: o.name })) }],
      onSubmit: (v) => Api.delete(`/departments/${d.id}?reassignTo=${encodeURIComponent(v.to)}`),
    });
    if (ok) { Toast.success('Department deleted', 'Employees were reassigned.'); load(); }
  }

  document.getElementById('btn-add').addEventListener('click', add);
  load();
})();
