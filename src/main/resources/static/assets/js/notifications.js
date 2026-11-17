/** WorkPulse — Notifications (persisted on the server; ownership enforced by the API). */
(async () => {
  'use strict';
  const user = await Auth.requireAuth();
  if (!user) return;
  await Layout.init('notifications', 'Notifications', user);
  const { h } = Ui;
  const $ = (id) => document.getElementById(id);
  let page = 0;

  async function load() {
    const list = $('notif-list');
    list.replaceChildren(h('p', { class: 'text-muted', style: 'padding:var(--space-5)' }, 'Loading…'));
    const r = await Api.get('/notifications' + Api.qs({ unreadOnly: $('n-filter').value === 'unread', page, size: 15 }));
    if (!r.ok) { list.replaceChildren(h('p', { class: 'text-error', style: 'padding:var(--space-5)' }, r.error)); return; }
    if (r.data.content.length === 0) {
      list.replaceChildren(h('div', { class: 'empty-state' }, h('div', { class: 'empty-title' }, 'You’re all caught up'),
        h('div', { class: 'empty-desc' }, 'No notifications to show.')));
      $('notif-pager').replaceChildren();
      return;
    }
    list.replaceChildren(...r.data.content.map(n => {
      const item = h('div', { class: `notification-item${n.read ? '' : ' unread'}`, tabindex: 0, role: 'button',
        'aria-label': `${n.title}${n.read ? '' : ' (unread)'}` },
        h('div', { class: 'notification-content' },
          h('div', { class: 'notification-title' }, n.title),
          h('div', { class: 'notification-message' }, n.message),
          h('div', { class: 'notification-time' }, Ui.fmtDateTime(n.createdAt))));
      const open = async () => {
        if (!n.read) await Api.post(`/notifications/${n.id}/read`, null);
        if (n.link && n.link.startsWith('/')) window.location.href = n.link; else load();
      };
      item.addEventListener('click', open);
      item.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(); } });
      return item;
    }));
    Ui.pager($('notif-pager'), r.data, (p) => { page = p; load(); });
  }

  $('n-filter').addEventListener('change', () => { page = 0; load(); });
  $('btn-all').addEventListener('click', async () => {
    const r = await Api.post('/notifications/read-all', null);
    if (r.ok) { Toast.success('All notifications marked as read'); const dot = document.getElementById('notif-dot'); if (dot) dot.style.display = 'none'; load(); }
    else Toast.error('Failed', r.error);
  });
  load();
})();
