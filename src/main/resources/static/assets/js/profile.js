/** WorkPulse — My profile and password change. */
(async () => {
  'use strict';
  const user = await Auth.requireAuth();
  if (!user) return;
  await Layout.init('profile', 'My Profile', user);
  const { h } = Ui;
  const $ = (id) => document.getElementById(id);

  async function load() {
    const r = await Api.get('/account/profile');
    if (!r.ok) { $('page-error').textContent = `Could not load your profile: ${r.error}`; $('page-error').hidden = false; return; }
    const p = r.data;
    $('p-name').value = p.name;
    $('p-phone').value = p.phone || '';
    const fact = (k, v) => [h('dt', { class: 'text-xs text-muted' }, k), h('dd', { style: 'margin:0 0 var(--space-3)' }, v || '—')];
    $('profile-facts').replaceChildren(...fact('Employee code', p.employeeCode), ...fact('Email', p.email), ...fact('Role', p.role),
      ...fact('Department', p.departmentName), ...fact('Designation', p.designation), ...fact('Manager', p.managerName),
      ...fact('Joined', Ui.fmtDate(p.joinDate)));
  }

  $('profile-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    $('profile-error').hidden = true;
    const r = await Api.put('/account/profile', { username: $('p-name').value.trim(), phone: $('p-phone').value.trim() });
    if (r.ok) { Toast.success('Profile saved'); load(); } else Ui.showResultError($('profile-error'), r);
  });

  $('pw-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    $('pw-error').hidden = true;
    if ($('pw-new').value !== $('pw-confirm').value) {
      $('pw-error').textContent = 'The new passwords do not match.'; $('pw-error').hidden = false; return;
    }
    const r = await Api.post('/account/password', { currentPassword: $('pw-current').value, newPassword: $('pw-new').value });
    if (r.ok) {
      Toast.success('Password changed', 'Please sign in again.');
      setTimeout(() => { window.location.href = '/login.html'; }, 1500);
    } else Ui.showResultError($('pw-error'), r);
  });
  load();
})();
