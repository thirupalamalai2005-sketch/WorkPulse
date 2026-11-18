/** WorkPulse — Reports with filters, preview and CSV export (admins: all; managers: direct reports). */
(async () => {
  'use strict';
  const user = await Auth.requireRole('ADMIN', 'MANAGER');
  if (!user) return;
  await Layout.init('reports', 'Reports', user);
  const { h } = Ui;
  const $ = (id) => document.getElementById(id);
  let page = 0;

  const depts = await Api.get('/departments');
  Ui.fillSelect($('r-dept'), depts.ok ? depts.data : [], 'id', d => d.name, 'All departments');
  $('r-from').value = Ui.monthStart();
  $('r-to').value = Ui.today();

  const type = () => $('r-type').value;
  const params = () => ({ from: $('r-from').value, to: $('r-to').value, departmentId: $('r-dept').value, q: $('r-q').value.trim(),
    status: type() === 'leaves' ? $('r-status').value : '' });

  const HEADS = {
    attendance: ['Employee', 'Date', 'Check in', 'Check out', 'Worked', 'Status'],
    leaves: ['Employee', 'Type', 'From', 'To', 'Days', 'Status', 'Reviewer'],
  };

  async function run() {
    $('rep-head').replaceChildren(h('tr', {}, HEADS[type()].map(c => h('th', {}, c))));
    const body = $('rep-body'), cols = HEADS[type()].length;
    Ui.tableMessage(body, cols, 'loading', 'Running report…');
    const r = await Api.get(`/reports/${type()}` + Api.qs({ ...params(), page, size: 20 }));
    if (!r.ok) { Ui.tableMessage(body, cols, 'error', r.error); $('rep-pager').replaceChildren(); return; }
    if (r.data.content.length === 0) { Ui.tableMessage(body, cols, 'empty', 'No data for these filters.'); $('rep-pager').replaceChildren(); return; }
    body.replaceChildren(...r.data.content.map(x => type() === 'attendance'
      ? h('tr', {}, h('td', {}, h('strong', {}, x.employeeName), h('div', { class: 'text-xs text-muted' }, x.employeeCode)),
          h('td', {}, Ui.fmtDate(x.workDate)), h('td', {}, Ui.fmtTime(x.checkInAt)), h('td', {}, Ui.fmtTime(x.checkOutAt)),
          h('td', {}, Ui.fmtMinutes(x.workedMinutes)), h('td', {}, x.missedCheckout ? Ui.badge('MISSED_CHECKOUT') : (x.late ? Ui.badge('LATE') : Ui.badge('PRESENT'))))
      : h('tr', {}, h('td', {}, h('strong', {}, x.employeeName), h('div', { class: 'text-xs text-muted' }, x.employeeCode)),
          h('td', {}, x.leaveTypeName), h('td', {}, Ui.fmtDate(x.startDate)), h('td', {}, Ui.fmtDate(x.endDate)),
          h('td', {}, String(x.days)), h('td', {}, Ui.badge(x.status)), h('td', {}, x.reviewerName || '—'))));
    Ui.pager($('rep-pager'), r.data, (p) => { page = p; run(); });
  }

  $('r-type').addEventListener('change', () => { $('r-status').hidden = type() !== 'leaves'; page = 0; run(); });
  $('r-apply').addEventListener('click', () => { page = 0; run(); });
  $('btn-csv').addEventListener('click', async () => {
    const r = await Api.download(`/reports/${type()}.csv` + Api.qs(params()), `${type()}-report.csv`);
    if (!r.ok) Toast.error('Export failed', r.error);
  });
  run();
})();
