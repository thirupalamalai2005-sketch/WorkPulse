/** WorkPulse — Calendar & policies: work schedule, leave types, holidays (admins edit, everyone reads). */
(async () => {
  'use strict';
  const user = await Auth.requireAuth();
  if (!user) return;
  await Layout.init('settings', 'Calendar & Policies', user);
  const { h } = Ui;
  const $ = (id) => document.getElementById(id);
  const admin = Auth.isAdmin(user);
  const DAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
  let schedule = null;

  // ---- schedule
  async function loadSchedule() {
    const r = await Api.get('/schedule');
    if (!r.ok) { $('sched-view').replaceChildren(h('p', { class: 'text-error' }, r.error)); return; }
    schedule = r.data;
    $('sched-view').replaceChildren(h('p', {}, `Working hours ${schedule.startTime.slice(0, 5)}–${schedule.endTime.slice(0, 5)}, `
      + `grace ${schedule.graceMinutes} min before “late”.`),
      h('p', {}, `Working days: ${schedule.workingDays.join(', ')}.`), h('p', { class: 'text-xs text-muted' }, `Company time zone: ${schedule.timezone}`));
    if (admin) $('btn-sched').hidden = false;
  }
  $('btn-sched').addEventListener('click', async () => {
    const dayFields = DAYS.map(d => ({ name: d, label: d, type: 'checkbox', value: schedule.workingDays.includes(d) }));
    const ok = await Ui.form({
      title: 'Edit work schedule',
      fields: [
        { name: 'startTime', label: 'Start time', type: 'time', required: true, value: schedule.startTime.slice(0, 5) },
        { name: 'endTime', label: 'End time', type: 'time', required: true, value: schedule.endTime.slice(0, 5) },
        { name: 'graceMinutes', label: 'Grace period (minutes)', type: 'number', min: 0, max: 120, required: true, value: schedule.graceMinutes },
        ...dayFields,
      ],
      onSubmit: (v) => Api.put('/schedule', { startTime: v.startTime, endTime: v.endTime, graceMinutes: Number(v.graceMinutes),
        workingDays: DAYS.filter(d => v[d]) }),
    });
    if (ok) { Toast.success('Schedule updated'); loadSchedule(); }
  });

  // ---- leave types
  async function loadTypes() {
    const body = $('lt-body');
    document.querySelector('#lt-body').closest('table').querySelector('thead tr').lastElementChild.hidden = !admin;
    const r = await Api.get('/leaves/types' + Api.qs({ includeInactive: admin }));
    if (!r.ok) { Ui.tableMessage(body, 5, 'error', r.error); return; }
    body.replaceChildren(...r.data.map(t => {
      const tr = h('tr', {}, h('td', {}, h('strong', {}, t.name)), h('td', {}, String(t.annualEntitlement)),
        h('td', {}, t.allowPastDates ? 'Allowed (≤30 days)' : 'Not allowed'), h('td', {}, Ui.badge(t.active ? 'ACTIVE' : 'INACTIVE')));
      if (admin) tr.append(h('td', {}, h('button', { type: 'button', class: 'btn btn-xs btn-secondary', onclick: () => editType(t) }, 'Edit')));
      return tr;
    }));
  }
  async function editType(t) {
    const ok = await Ui.form({
      title: `Edit ${t.name}`,
      intro: 'Changing the entitlement applies to balances immediately (remaining = entitlement − approved − pending).',
      fields: [
        { name: 'name', label: 'Name', required: true, value: t.name, maxlength: 60 },
        { name: 'annualEntitlement', label: 'Days per year', type: 'number', min: 0, max: 365, required: true, value: t.annualEntitlement },
        { name: 'allowPastDates', label: 'Allow back-dated requests (up to 30 days)', type: 'checkbox', value: t.allowPastDates },
        { name: 'active', label: 'Active', type: 'checkbox', value: t.active },
      ],
      onSubmit: (v) => Api.put(`/leaves/types/${t.id}`, { name: v.name, annualEntitlement: Number(v.annualEntitlement),
        allowPastDates: v.allowPastDates, active: v.active }),
    });
    if (ok) { Toast.success('Leave type updated'); loadTypes(); }
  }

  // ---- holidays
  const yearSel = $('h-year');
  const thisYear = Number(Ui.today().slice(0, 4));
  for (let y = thisYear - 1; y <= thisYear + 2; y++) yearSel.append(h('option', { value: y, selected: y === thisYear }, String(y)));
  async function loadHolidays() {
    const body = $('hol-body');
    document.querySelector('#hol-body').closest('table').querySelector('thead tr').lastElementChild.hidden = !admin;
    Ui.tableMessage(body, admin ? 3 : 2, 'loading', 'Loading…');
    const r = await Api.get('/holidays' + Api.qs({ year: yearSel.value }));
    if (!r.ok) { Ui.tableMessage(body, 3, 'error', r.error); return; }
    if (r.data.length === 0) { Ui.tableMessage(body, admin ? 3 : 2, 'empty', 'No holidays defined for this year.'); return; }
    body.replaceChildren(...r.data.map(x => {
      const tr = h('tr', {}, h('td', {}, Ui.fmtDate(x.date)), h('td', {}, x.name));
      if (admin) tr.append(h('td', {}, h('button', { type: 'button', class: 'btn btn-xs btn-danger-outline', onclick: () => removeHoliday(x) }, 'Delete')));
      return tr;
    }));
  }
  async function removeHoliday(x) {
    const yes = await Ui.confirm({ title: 'Delete holiday?', message: `${x.name} (${Ui.fmtDate(x.date)})`, confirmText: 'Delete', danger: true });
    if (!yes) return;
    const r = await Api.delete(`/holidays/${x.id}`);
    if (r.ok) { Toast.success('Holiday deleted'); loadHolidays(); } else Toast.error('Not possible', r.error);
  }
  if (admin) {
    $('btn-holiday').hidden = false;
    $('btn-holiday').addEventListener('click', async () => {
      const ok = await Ui.form({ title: 'Add holiday', submitText: 'Add',
        fields: [{ name: 'date', label: 'Date', type: 'date', required: true }, { name: 'name', label: 'Name', required: true, maxlength: 100 }],
        onSubmit: (v) => Api.post('/holidays', v) });
      if (ok) { Toast.success('Holiday added'); loadHolidays(); }
    });
  }
  yearSel.addEventListener('change', loadHolidays);
  loadSchedule(); loadTypes(); loadHolidays();
})();
