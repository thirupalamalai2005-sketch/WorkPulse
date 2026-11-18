/** WorkPulse — Leave: balances, requests, cancellation and manager/admin approvals. */
(async () => {
  'use strict';
  const user = await Auth.requireAuth();
  if (!user) return;
  await Layout.init('leaves', 'Leave & Requests', user);
  const { h } = Ui;
  const $ = (id) => document.getElementById(id);
  const reviewer = Auth.isReviewer(user);
  let types = [];

  async function loadBalances() {
    const r = await Api.get('/leaves/balances');
    const grid = $('balance-grid');
    if (!r.ok) { grid.replaceChildren(h('p', { class: 'text-error' }, r.error)); return; }
    grid.replaceChildren(...r.data.map(b => h('div', { class: 'leave-balance-card' },
      h('div', { class: 'leave-balance-type' }, b.leaveTypeName),
      h('div', { class: 'leave-balance-days' }, `${b.remaining} left`),
      h('div', { class: 'text-xs text-muted' }, `${b.used} used · ${b.pending} pending · ${b.entitlement} per year`))));
  }

  // ---- my requests
  let myPage = 0;
  async function loadMine() {
    const body = $('my-body');
    Ui.tableMessage(body, 8, 'loading', 'Loading…');
    const r = await Api.get('/leaves' + Api.qs({ status: $('m-status').value, page: myPage, size: CONFIG.PAGE_SIZE }));
    if (!r.ok) { Ui.tableMessage(body, 8, 'error', r.error); return; }
    if (r.data.content.length === 0) { Ui.tableMessage(body, 8, 'empty', 'No leave requests yet.'); $('my-pager').replaceChildren(); return; }
    body.replaceChildren(...r.data.content.map(l => h('tr', {},
      h('td', {}, l.leaveTypeName), h('td', {}, Ui.fmtDate(l.startDate)), h('td', {}, Ui.fmtDate(l.endDate)), h('td', {}, String(l.days)),
      h('td', {}, l.reason), h('td', {}, Ui.badge(l.status)),
      h('td', {}, l.reviewerName ? `${l.reviewerName}${l.reviewNote ? ': ' + l.reviewNote : ''}` : '—'),
      h('td', {}, l.cancellable ? h('button', { type: 'button', class: 'btn btn-xs btn-danger-outline', onclick: () => cancel(l) }, 'Cancel') : null))));
    Ui.pager($('my-pager'), r.data, (p) => { myPage = p; loadMine(); });
  }
  async function cancel(l) {
    const yes = await Ui.confirm({ title: 'Cancel leave request?', danger: true, confirmText: 'Cancel request',
      message: `${l.leaveTypeName}: ${Ui.fmtDate(l.startDate)} to ${Ui.fmtDate(l.endDate)}. Days return to your balance.` });
    if (!yes) return;
    const r = await Api.post(`/leaves/${l.id}/cancel`, null);
    if (r.ok) { Toast.success('Request cancelled'); loadMine(); loadBalances(); } else Toast.error('Not possible', r.error);
  }

  async function apply() {
    if (types.length === 0) { const t = await Api.get('/leaves/types'); if (t.ok) types = t.data; }
    const ok = await Ui.form({
      title: 'Apply for leave', submitText: 'Submit request',
      intro: 'Only working days (no weekends or company holidays) are counted against your balance.',
      fields: [
        { name: 'leaveTypeId', label: 'Leave type', type: 'select', required: true, options: types.map(t => ({ value: t.id, label: t.name })) },
        { name: 'startDate', label: 'From', type: 'date', required: true, value: Ui.today() },
        { name: 'endDate', label: 'To', type: 'date', required: true, value: Ui.today() },
        { name: 'reason', label: 'Reason', type: 'textarea', required: true },
      ],
      onSubmit: (v) => Api.post('/leaves', { leaveTypeId: Number(v.leaveTypeId), startDate: v.startDate, endDate: v.endDate, reason: v.reason }),
    });
    if (ok) { Toast.success('Request submitted', 'Your approver has been notified.'); loadMine(); loadBalances(); }
  }

  // ---- approvals
  let rvPage = 0;
  async function loadReview() {
    const body = $('rv-body');
    Ui.tableMessage(body, 8, 'loading', 'Loading…');
    const r = await Api.get('/leaves/review' + Api.qs({ status: $('rv-status').value, page: rvPage, size: CONFIG.PAGE_SIZE }));
    if (!r.ok) { Ui.tableMessage(body, 8, 'error', r.error); return; }
    if (r.data.content.length === 0) { Ui.tableMessage(body, 8, 'empty', 'No requests to show.'); $('rv-pager').replaceChildren(); return; }
    body.replaceChildren(...r.data.content.map(l => h('tr', {},
      h('td', {}, h('strong', {}, l.employeeName), h('div', { class: 'text-xs text-muted' }, l.employeeCode)),
      h('td', {}, l.leaveTypeName), h('td', {}, Ui.fmtDate(l.startDate)), h('td', {}, Ui.fmtDate(l.endDate)), h('td', {}, String(l.days)),
      h('td', {}, l.reason), h('td', {}, Ui.badge(l.status)),
      h('td', {}, l.status === 'PENDING' ? h('div', { style: 'display:flex;gap:4px' },
        h('button', { type: 'button', class: 'btn btn-xs btn-success', onclick: () => decide(l, 'approve') }, 'Approve'),
        h('button', { type: 'button', class: 'btn btn-xs btn-danger-outline', onclick: () => decide(l, 'reject') }, 'Reject')) : (l.reviewerName || '—')))));
    Ui.pager($('rv-pager'), r.data, (p) => { rvPage = p; loadReview(); });
  }
  async function decide(l, kind) {
    const res = await Ui.confirm({
      title: `${kind === 'approve' ? 'Approve' : 'Reject'} leave request?`, danger: kind === 'reject',
      message: `${l.employeeName}: ${l.leaveTypeName}, ${Ui.fmtDate(l.startDate)} to ${Ui.fmtDate(l.endDate)} (${l.days} day(s))`,
      confirmText: kind === 'approve' ? 'Approve' : 'Reject',
      withNote: { placeholder: kind === 'reject' ? 'Reason (required)' : 'Optional note', required: kind === 'reject' },
    });
    if (!res) return;
    const r = await Api.post(`/leaves/${l.id}/${kind}`, { note: res.note });
    if (r.ok) { Toast.success(kind === 'approve' ? 'Approved' : 'Rejected'); loadReview(); } else Toast.error('Not possible', r.error);
  }

  $('m-status').addEventListener('change', () => { myPage = 0; loadMine(); });
  $('rv-status').addEventListener('change', () => { rvPage = 0; loadReview(); });
  $('btn-apply').addEventListener('click', apply);
  const defs = [{ id: 'mine', label: 'My requests', onShow: loadMine }];
  if (reviewer) defs.push({ id: 'review', label: 'Approvals', onShow: loadReview });
  Ui.tabs($('tabs'), defs);
  loadBalances();
})();
