/**
 * WorkPulse — Layout Manager
 *
 * Renders and initialises the shared app shell:
 * sidebar navigation, topbar, logout, mobile responsiveness.
 *
 * Usage on protected pages:
 *   await Layout.init('dashboard', user);
 */

const Layout = (() => {
  'use strict';

  /* ── SVG Icon Set ────────────────────────────────────────────── */
  const ICONS = {
    dashboard:     `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg>`,
    employees:     `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></svg>`,
    attendance:    `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/><polyline points="9 16 11 18 15 14"/></svg>`,
    leaves:        `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M21 3s-3 3-7.5 3S6 3 6 3 3 6 3 10.5C3 17 9 21 12 21s9-4 9-10.5C21 6 21 3 21 3z"/><path d="M12 21V9"/><path d="M9 12l3-3 3 3"/></svg>`,
    notifications: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 01-3.46 0"/></svg>`,
    auditLogs:     `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2"/><rect x="9" y="3" width="6" height="4" rx="1"/><line x1="9" y1="12" x2="15" y2="12"/><line x1="9" y1="16" x2="13" y2="16"/></svg>`,
    profile:       `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`,
    logout:        `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>`,
    menu:          `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>`,
    bell:          `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 01-3.46 0"/></svg>`,
    chevronDown:   `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>`,
  };

  /* ── Navigation Definition ───────────────────────────────────── */
  const ALL = ['ADMIN','MANAGER','EMPLOYEE'];
  const NAV_ITEMS = [
    { id: 'dashboard',    label: 'Dashboard',          icon: 'dashboard',     href: '/dashboard.html',    roles: ALL },
    { id: 'employees',    label: 'Employees',          icon: 'employees',     href: '/employees.html',    roles: ALL },
    { id: 'departments',  label: 'Departments',        icon: 'employees',     href: '/departments.html',  roles: ['ADMIN'] },
    { id: 'attendance',   label: 'Attendance',         icon: 'attendance',    href: '/attendance.html',   roles: ALL },
    { id: 'leaves',       label: 'Leave & Requests',   icon: 'leaves',        href: '/leaves.html',       roles: ALL },
    { id: 'reports',      label: 'Reports',            icon: 'auditLogs',     href: '/reports.html',      roles: ['ADMIN','MANAGER'] },
    { id: 'settings',     label: 'Calendar & Policies',icon: 'attendance',    href: '/settings.html',     roles: ALL },
    { id: 'notifications',label: 'Notifications',      icon: 'notifications', href: '/notifications.html',roles: ALL },
    { id: 'audit-logs',   label: 'Audit Logs',         icon: 'auditLogs',     href: '/audit-logs.html',   roles: ['ADMIN'] },
    { id: 'profile',      label: 'My Profile',         icon: 'profile',       href: '/profile.html',      roles: ALL },
  ];
  const ROLE_LABEL = { ADMIN: 'Administrator', MANAGER: 'Manager', EMPLOYEE: 'Employee' };

  /* ── Sidebar HTML Builder ────────────────────────────────────── */
  function buildSidebar(activePage, user) {
    const role = user?.role || 'EMPLOYEE';

    const navItems = NAV_ITEMS
      .filter(item => item.roles.includes(role))
      .map(item => {
        const isActive = item.id === activePage;
        return `
          <li class="nav-item">
            <a href="${item.href}" class="nav-link ${isActive ? 'active' : ''}" data-page="${item.id}">
              <span class="nav-icon">${ICONS[item.icon]}</span>
              <span class="nav-text">${escapeHtml(item.label)}</span>
            </a>
          </li>`;
      }).join('');

    const initials = Auth.getInitials(user?.username || user?.name || 'U');
    const userName  = escapeHtml(user?.username || user?.name || 'Unknown');
    const userRole  = escapeHtml(user?.role || 'Employee');

    return `
      <aside class="sidebar" id="sidebar">
        <a href="/dashboard.html" class="sidebar-brand">
          <div class="sidebar-brand-icon">WP</div>
          <div class="sidebar-brand-text">
            <span class="sidebar-brand-name">WorkPulse</span>
            <span class="sidebar-brand-tagline">People Ops, in Sync</span>
          </div>
        </a>

        <nav class="sidebar-nav" aria-label="Main navigation">
          <ul class="nav-section">
            ${navItems}
          </ul>
        </nav>

        <div class="sidebar-footer">
          <button class="sidebar-user" id="sidebar-logout-btn" title="Logout">
            <div class="user-avatar" aria-hidden="true">${initials}</div>
            <div class="sidebar-user-info">
              <div class="sidebar-user-name">${userName}</div>
              <div class="sidebar-user-role">${ROLE_LABEL[user?.role] || 'Employee'}</div>
            </div>
            <span class="nav-icon" style="flex-shrink:0">${ICONS.logout}</span>
          </button>
        </div>
      </aside>
      <div class="sidebar-overlay" id="sidebar-overlay"></div>
    `;
  }

  /* ── Topbar HTML Builder ─────────────────────────────────────── */
  function buildTopbar(pageTitle, user) {
    const initials = Auth.getInitials(user?.username || user?.name || 'U');
    const userName  = escapeHtml(user?.username || user?.name || 'Unknown');
    const userRole  = ROLE_LABEL[user?.role] || 'Employee';

    return `
      <header class="topbar" id="topbar">
        <div class="topbar-left">
          <button class="topbar-menu-btn" id="topbar-menu-btn" aria-label="Toggle menu">
            ${ICONS.menu}
          </button>
          <h1 class="topbar-title">${escapeHtml(pageTitle)}</h1>
        </div>

        <div class="topbar-right">
          <a href="/notifications.html" class="topbar-action-btn" id="topbar-notif-btn" title="Notifications" aria-label="Notifications">
            ${ICONS.bell}
            <span class="badge-dot" id="notif-dot" style="display:none"></span>
          </a>

          <div class="topbar-divider"></div>

          <button class="topbar-user" id="topbar-user-btn" aria-label="User menu">
            <div class="user-avatar sm" aria-hidden="true">${initials}</div>
            <div class="topbar-user-info">
              <div class="topbar-user-name">${userName}</div>
              <div class="topbar-user-role">${escapeHtml(userRole)}</div>
            </div>
            <span style="color:var(--color-text-muted);display:flex;align-items:center">${ICONS.chevronDown}</span>
          </button>

          <div class="dropdown-menu" id="user-dropdown">
            <a href="/profile.html" class="dropdown-item">
              ${ICONS.profile}
              My Profile
            </a>
            <div class="dropdown-divider"></div>
            <button class="dropdown-item danger" id="topbar-logout-btn">
              ${ICONS.logout}
              Logout
            </button>
          </div>
        </div>
      </header>
    `;
  }

  /* ── Inject Layout into Page ─────────────────────────────────── */
  function inject(activePage, pageTitle, user) {
    // Create a wrapper around the existing page body content
    const existingContent = document.body.innerHTML;

    document.body.innerHTML = `
      <div class="app-shell">
        ${buildSidebar(activePage, user)}
        ${buildTopbar(pageTitle, user)}
        <main class="main-content" id="main-content">
          ${existingContent}
        </main>
      </div>
    `;

    _attachEvents();
  }

  /* ── Attach Event Listeners ──────────────────────────────────── */
  function _attachEvents() {
    // Mobile sidebar toggle
    const menuBtn    = document.getElementById('topbar-menu-btn');
    const sidebar    = document.getElementById('sidebar');
    const overlay    = document.getElementById('sidebar-overlay');

    if (menuBtn && sidebar && overlay) {
      menuBtn.addEventListener('click', () => {
        sidebar.classList.toggle('open');
        overlay.classList.toggle('active');
      });

      overlay.addEventListener('click', () => {
        sidebar.classList.remove('open');
        overlay.classList.remove('active');
      });
    }

    // Close sidebar on nav link click (mobile)
    document.querySelectorAll('.nav-link').forEach(link => {
      link.addEventListener('click', () => {
        if (window.innerWidth <= 768) {
          sidebar?.classList.remove('open');
          overlay?.classList.remove('active');
        }
      });
    });

    // User dropdown toggle
    const userBtn      = document.getElementById('topbar-user-btn');
    const userDropdown = document.getElementById('user-dropdown');

    if (userBtn && userDropdown) {
      userBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        userDropdown.classList.toggle('open');
      });

      document.addEventListener('click', () => {
        userDropdown.classList.remove('open');
      });
    }

    // Logout buttons
    const logoutBtns = [
      document.getElementById('sidebar-logout-btn'),
      document.getElementById('topbar-logout-btn'),
    ];

    logoutBtns.forEach(btn => {
      if (btn) {
        btn.addEventListener('click', async () => {
          await Auth.logout();
        });
      }
    });
  }

  /* ── Notification indicator ──────────────────────────────────── */
  async function updateNotifIndicator() {
    try {
      const result = await Api.get('/notifications/unread-count');
      const dot = document.getElementById('notif-dot');
      if (dot && result.ok) dot.style.display = result.data.unread > 0 ? 'block' : 'none';
    } catch { /* non-critical */ }
  }

  /* ── Public Init ─────────────────────────────────────────────── */

  /**
   * Initialize the layout on a protected page.
   * @param {string} pageId    - matches a NAV_ITEMS id (e.g. 'dashboard')
   * @param {string} pageTitle - shown in topbar
   * @param {object} user      - authenticated user object
   */
  async function init(pageId, pageTitle, user) {
    inject(pageId, pageTitle, user);
    // Company time zone: all times are formatted in it, whatever the browser's own zone is
    const schedule = await Api.get('/schedule');
    if (schedule.ok && schedule.data) Ui.setTz(schedule.data.timezone);
    updateNotifIndicator();
  }

  return { init, ICONS };
})();
