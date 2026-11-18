/** WorkPulse — Attendance: check-in/out, history, summaries, corrections, team roster. */
(async () => {
  'use strict';
  const user = await Auth.requireAuth();
  if (!user) return;
  await Layout.init('attendance', 'Attendance', user);
  const { h } = Ui;
  const reviewer = Auth.isReviewer(user);
  const $ = (id) => document.getElementById(id);
  const errorBox = $('page-error');

  // ---- date helpers (plain YYYY-MM-DD arithmetic, no time-zone surprises)
  const addDays = (s, n) => { const d = new Date(s + 'T00:00:00Z'); d.setUTCDate(d.getUTCDate() + n); return d.toISOString().slice(0, 10); };
  const weekStart = (s) => { const dow = new Date(s + 'T00:00:00Z').getUTCDay(); return addDays(s, -((dow + 6) % 7)); };

  // ---- live clock (company time zone)
  const tick = () => {
    const now = new Date();
    $('clock-display').textContent = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false, timeZone: Ui.getTz() }).format(now);
    $('clock-date-display').textContent = `${Ui.fmtDate(Ui.today())} · ${Ui.getTz() || ''}`;
  };
  tick(); setInterval(tick, 1000);

  // ---- today's status + buttons
  const btnIn = $('btn-checkin'), btnOut = $('btn-checkout'), msg = $('clock-status-msg');
  async function loadToday() {
    const r = await Api.get('/attendance/today');
    if (!r.ok) { msg.textContent = r.error; btnIn.disabled = btnOut.disabled = true; return; }
    const t = r.data;
    btnIn.disabled = t.state !== 'NOT_CHECKED_IN';
    btnOut.disabled = t.state !== 'CHECKED_IN';
    if (t.state === 'NOT_CHECKED_IN') msg.textContent = t.onLeave ? 'You are on approved leave today.' : (t.dayNote || 'Ready to check in');
    else if (t.state === 'CHECKED_IN') msg.textContent = `Checked in at ${Ui.fmtTime(t.record.checkInAt)}${t.record.late ? ' (late)' : ''}`;
    else msg.textContent = `Checked in ${Ui.fmtTime(t.record.checkInAt)} · out ${Ui.fmtTime(t.record.checkOutAt)} · ${Ui.fmtMinutes(t.record.workedMinutes)}`;
  }
  async function act(path, okTitle) {
    btnIn.disabled = btnOut.disabled = true;
    const r = await Api.post(path, null);
    if (r.ok) Toast.success(okTitle); else Toast.error('Not possible', r.error);
    await loadToday(); loadSummary(); loadHistory();
  }
  btnIn.addEventListener('click', () => act('/attendance/check-in', 'Checked in'));
  btnOut.addEventListener('click', () => act('/attendance/check-out', 'Checked out'));

  // ---- my summary + history
  const from = $('h-from'), to = $('h-to');
  from.value = Ui.monthStart(); to.value = Ui.today();
  let histPage = 0;
  const stat = (label, value) => h('div', { class: 'stat-card' }, h('div', { class: 'stat-card-label' }, label), h('div', { class: 'stat-card-value' }, String(value)));

  async function loadSummary() {
    const r = await Api.get('/attendance/summary' + Api.qs({ from: from.value, to: to.value }));
    if (!r.ok) { $('my-summary').replaceChildren(h('p', { class: 'text-error' }, r.error)); return; }
    const s = r.data;
    $('my-summary').replaceChildren(stat('Working days', s.workingDays), stat('Present', s.presentDays), stat('Late', s.lateDays),
      stat('Absent', s.absentDays), stat('On leave', s.leaveDays), stat('Missed check-outs', s.missedCheckouts),
      stat('Hours worked', Ui.fmtMinutes(s.totalWorkedMinutes)));
  }
  async function loadHistory() {
    const body = $('hist-body');
    Ui.tableMessage(body, 6, 'loading', 'Loading…');
    const r = await Api.get('/attendance' + Api.qs({ from: from.value, to: to.value, page: histPage, size: CONFIG.PAGE_SIZE }));
    if (!r.ok) { Ui.tableMessage(body, 6, 'error', r.error); return; }
    if (r.data.content.length === 0) { Ui.tableMessage(body, 6, 'empty', 'No attendance records in this range.'); $('hist-pager').replaceChildren(); return; }
    body.replaceChildren(...r.data.content.map(a => h('tr', {},
      h('td', {}, Ui.fmtDate(a.workDate)), h('td', {}, Ui.fmtTime(a.checkInAt)), h('td', {}, Ui.fmtTime(a.checkOutAt)),
      h('td', {}, Ui.fmtMinutes(a.workedMinutes)),
      h('td', {}, a.missedCheckout ? Ui.badge('MISSED_CHECKOUT') : (a.late ? Ui.badge('LATE') : Ui.badge('PRESENT'))),
      h('td', {}, a.source === 'CORRECTION' ? 'Corrected' : 'Check-in'))));
    Ui.pager($('hist-pager'), r.data, (p) => { histPage = p; loadHistory(); });
  }
  const refreshMine = () => { histPage = 0; loadSummary(); loadHistory(); };
  $('h-apply').addEventListener('click', refreshMine);
  document.querySelectorAll('[data-range]').forEach(b => b.addEventListener('click', () => {
    from.value = b.dataset.range === 'week' ? weekStart(Ui.today()) : Ui.monthStart(); to.value = Ui.today(); refreshMine();
  }));

  // ---- my correction requests
  let corrPage = 0;
  async function loadCorrections() {
    const body = $('corr-body');
    Ui.tableMessage(body, 6, 'loading', 'Loading…');
    const r = await Api.get('/attendance/corrections/mine' + Api.qs({ page: corrPage, size: CONFIG.PAGE_SIZE }));
    if (!r.ok) { Ui.tableMessage(body, 6, 'error', r.error); return; }
    if (r.data.content.length === 0) { Ui.tableMessage(body, 6, 'empty', 'You have not requested any corrections.'); $('corr-pager').replaceChildren(); return; }
    body.replaceChildren(...r.data.content.map(c => h('tr', {},
      h('td', {}, Ui.fmtDate(c.workDate)), h('td', {}, Ui.fmtTime(c.requestedCheckIn)), h('td', {}, Ui.fmtTime(c.requestedCheckOut)),
      h('td', {}, c.reason), h('td', {}, Ui.badge(c.status)), h('td', {}, c.reviewNote || '—'))));
    Ui.pager($('corr-pager'), r.data, (p) => { corrPage = p; loadCorrections(); });
  }
  $('btn-corr').addEventListener('click', async () => {
    const ok = await Ui.form({
      title: 'Request attendance correction', submitText: 'Submit request',
      intro: 'Times are in the company time zone. Provide the check-in time, the check-out time, or both.',
      fields: [
        { name: 'workDate', label: 'Date', type: 'date', required: true, value: Ui.today(), max: Ui.today() },
        { name: 'checkInTime', label: 'Check-in time', type: 'time' },
        { name: 'checkOutTime', label: 'Check-out time', type: 'time' },
        { name: 'reason', label: 'Reason', type: 'textarea', required: true },
      ],
      onSubmit: (v) => Api.post('/attendance/corrections', { workDate: v.workDate, checkInTime: v.checkInTime || null, checkOutTime: v.checkOutTime || null, reason: v.reason }),
    });
    if (ok) { Toast.success('Request submitted', 'Your manager has been notified.'); loadCorrections(); }
  });

  // ---- reviewer: team roster
  let rosterPage = 0;
  async function loadRoster() {
    const body = $('roster-body');
    Ui.tableMessage(body, 5, 'loading', 'Loading…');
    const r = await Api.get('/attendance/roster' + Api.qs({ date: $('r-date').value, page: rosterPage, size: CONFIG.PAGE_SIZE }));
    if (!r.ok) { Ui.tableMessage(body, 5, 'error', r.error); return; }
    if (r.data.content.length === 0) { Ui.tableMessage(body, 5, 'empty', 'No employees in your scope.'); $('roster-pager').replaceChildren(); return; }
    body.replaceChildren(...r.data.content.map(e => h('tr', {},
      h('td', {}, h('strong', {}, e.employeeName), h('div', { class: 'text-xs text-muted' }, e.employeeCode)),
      h('td', {}, e.departmentName || '—'), h('td', {}, Ui.badge(e.status)),
      h('td', {}, Ui.fmtTime(e.checkInAt)), h('td', {}, Ui.fmtTime(e.checkOutAt)))));
    Ui.pager($('roster-pager'), r.data, (p) => { rosterPage = p; loadRoster(); });
  }

  // ---- reviewer: approve / reject corrections
  let rvPage = 0;
  async function loadReview() {
    const body = $('rv-body');
    Ui.tableMessage(body, 7, 'loading', 'Loading…');
    const r = await Api.get('/attendance/corrections/review' + Api.qs({ status: $('rv-status').value, page: rvPage, size: CONFIG.PAGE_SIZE }));
    if (!r.ok) { Ui.tableMessage(body, 7, 'error', r.error); return; }
    if (r.data.content.length === 0) { Ui.tableMessage(body, 7, 'empty', 'Nothing to review.'); $('rv-pager').replaceChildren(); return; }
    body.replaceChildren(...r.data.content.map(c => h('tr', {},
      h('td', {}, h('strong', {}, c.employeeName), h('div', { class: 'text-xs text-muted' }, c.employeeCode)),
      h('td', {}, Ui.fmtDate(c.workDate)), h('td', {}, Ui.fmtTime(c.requestedCheckIn)), h('td', {}, Ui.fmtTime(c.requestedCheckOut)),
      h('td', {}, c.reason), h('td', {}, Ui.badge(c.status)),
      h('td', {}, c.status === 'PENDING' ? h('div', { style: 'display:flex;gap:4px' },
        h('button', { type: 'button', class: 'btn btn-xs btn-success', onclick: () => decide(c, 'approve') }, 'Approve'),
        h('button', { type: 'button', class: 'btn btn-xs btn-danger-outline', onclick: () => decide(c, 'reject') }, 'Reject')) : (c.reviewerName || '—')))));
    Ui.pager($('rv-pager'), r.data, (p) => { rvPage = p; loadReview(); });
  }
  async function decide(c, kind) {
    const res = await Ui.confirm({
      title: `${kind === 'approve' ? 'Approve' : 'Reject'} correction?`, danger: kind === 'reject',
      message: `${c.employeeName} — ${Ui.fmtDate(c.workDate)}`, confirmText: kind === 'approve' ? 'Approve' : 'Reject',
      withNote: { placeholder: kind === 'reject' ? 'Reason (required)' : 'Optional note', required: kind === 'reject' },
    });
    if (!res) return;
    const r = await Api.post(`/attendance/corrections/${c.id}/${kind}`, { note: res.note });
    if (r.ok) { Toast.success(kind === 'approve' ? 'Approved' : 'Rejected'); loadReview(); } else Toast.error('Not possible', r.error);
  }

  // ---- tabs
  $('r-date').value = Ui.today();
  $('r-date').addEventListener('change', () => { rosterPage = 0; loadRoster(); });
  $('rv-status').addEventListener('change', () => { rvPage = 0; loadReview(); });
  const defs = [
    { id: 'mine', label: 'My attendance', onShow: refreshMine },
    { id: 'corr', label: 'My corrections', onShow: loadCorrections },
  ];
  if (reviewer) defs.push({ id: 'roster', label: user.role === 'ADMIN' ? 'Company roster' : 'Team roster', onShow: loadRoster },
                          { id: 'review', label: 'Correction approvals', onShow: loadReview });
  Ui.tabs($('tabs'), defs);
  loadToday();
})();
