/** WorkPulse — Audit log viewer (administrators, read-only). */
(async () => {
  'use strict';
  const user = await Auth.requireAdmin();
  if (!user) return;
  await Layout.init('audit-logs', 'Audit Logs', user);
  const { h } = Ui;
  const $ = (id) => document.getElementById(id);
  let page = 0;

  async function load() {
    const body = $('audit-body');
    Ui.tableMessage(body, 6, 'loading', 'Loading audit log…');
    const r = await Api.get('/audit-logs' + Api.qs({ action: $('a-action').value.trim(), actor: $('a-actor').value.trim(),
      resourceType: $('a-type').value.trim(), from: $('a-from').value, to: $('a-to').value, page, size: 20 }));
    if (!r.ok) { Ui.tableMessage(body, 6, 'error', r.error); $('audit-pager').replaceChildren(); return; }
    if (r.data.content.length === 0) { Ui.tableMessage(body, 6, 'empty', 'No audit entries match.'); $('audit-pager').replaceChildren(); return; }
    body.replaceChildren(...r.data.content.map(a => h('tr', {},
      h('td', {}, Ui.fmtDateTime(a.createdAt)), h('td', {}, a.actorEmail || 'system'),
      h('td', {}, h('span', { class: 'audit-action-tag' }, a.action)),
      h('td', {}, `${a.resourceType}${a.resourceId ? ' #' + a.resourceId : ''}`),
      h('td', {}, a.details || '—'), h('td', {}, a.ipAddress || '—'))));
    Ui.pager($('audit-pager'), r.data, (p) => { page = p; load(); });
  }
  $('a-apply').addEventListener('click', () => { page = 0; load(); });
  $('a-clear').addEventListener('click', () => {
    ['a-action', 'a-actor', 'a-type', 'a-from', 'a-to'].forEach(id => { $(id).value = ''; });
    page = 0; load();
  });
  load();
})();
