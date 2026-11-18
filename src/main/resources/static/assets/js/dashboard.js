/** WorkPulse — Dashboard. Every number comes from GET /api/dashboard/summary (persisted data). */
(async () => {
  'use strict';
  const user = await Auth.requireAuth();
  if (!user) return;
  await Layout.init('dashboard', 'Dashboard', user);
  const { h } = Ui;

  const errorBox = document.getElementById('page-error');
  const card = (label, value, desc, tone) => h('div', { class: 'stat-card' },
    h('div', { class: 'stat-card-label' }, label),
    h('div', { class: `stat-card-value${String(value).length > 7 ? ' is-text' : ''}` }, String(value)),
    desc ? h('div', { class: 'stat-card-desc' }, desc) : null);

  const personal = document.getElementById('personal-stats');
  personal.replaceChildren(h('span', { class: 'spinner spinner-sm' }));

  const result = await Api.get('/dashboard/summary');
  if (!result.ok) {
    personal.replaceChildren();
    errorBox.textContent = `Could not load the dashboard: ${result.error}`;
    errorBox.hidden = false;
    return;
  }
  const d = result.data;

  if (d.workforce) {
    const w = d.workforce;
    document.getElementById('workforce-section').hidden = false;
    document.getElementById('workforce-title').textContent = w.scope === 'ORGANIZATION' ? 'Workforce today' : 'My team today';
    document.getElementById('workforce-stats').replaceChildren(
      card('Active employees', w.totalEmployees),
      card('Present', w.presentToday, w.workingDay ? `${w.attendanceRate}% attendance` : 'Non-working day'),
      card('Late', w.lateToday),
      card('On leave', w.onLeaveToday),
      card('Absent / not in yet', w.absentToday),
      card('Pending leave requests', w.pendingLeaves),
      card('Pending corrections', w.pendingCorrections));
  }

  const p = d.personal, m = p.monthSummary;
  const stateLabel = { NOT_CHECKED_IN: 'Not checked in', CHECKED_IN: 'Checked in', CHECKED_OUT: 'Checked out' }[p.todayState];
  personal.replaceChildren(
    card('Today', stateLabel, 'Go to Attendance to check in/out'),
    card('Present this month', m.presentDays, `${m.workingDays} working days so far`),
    card('Late this month', m.lateDays),
    card('Absent this month', m.absentDays, `${m.leaveDays} leave day(s)`),
    card('My pending leave requests', p.myPendingLeaves),
    card('Unread notifications', p.unreadNotifications));

  const grid = document.getElementById('balance-grid');
  if (p.leaveBalances.length === 0) grid.replaceChildren(h('p', { class: 'text-muted' }, 'No leave types configured.'));
  else grid.replaceChildren(...p.leaveBalances.map(b => h('div', { class: 'leave-balance-card' },
    h('div', { class: 'leave-balance-type' }, b.leaveTypeName),
    h('div', { class: 'leave-balance-days' }, `${b.remaining} / ${b.entitlement}`),
    h('div', { class: 'text-xs text-muted' }, `${b.used} used · ${b.pending} pending`))));

  if (user.role === 'ADMIN') {
    document.getElementById('activity-card').hidden = false;
    const list = document.getElementById('activity-list');
    if (d.recentActivity.length === 0) list.replaceChildren(h('li', { class: 'text-muted' }, 'No activity recorded yet.'));
    else list.replaceChildren(...d.recentActivity.map(a => h('li', { class: 'activity-item' },
      h('div', { class: 'activity-content' },
        h('div', { class: 'activity-text' }, `${a.action.replace(/_/g, ' ').toLowerCase()} — ${a.actorEmail || 'system'}`),
        h('div', { class: 'activity-time' }, Ui.fmtDateTime(a.createdAt))))));
  }
})();
