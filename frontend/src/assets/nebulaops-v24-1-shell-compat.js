(() => {
  const styleId = 'nebulaops-v24-1-shell-compat-style';
  if (!document.getElementById(styleId)) {
    const style = document.createElement('style');
    style.id = styleId;
    style.textContent = `
      body .shell-live-banner.shell-live-banner{display:none!important}
      body .shell-layout.shell-layout{grid-template-columns:280px minmax(0,1fr)!important;grid-template-rows:1fr!important}
      body .shell-sidebar.shell-sidebar{grid-column:1!important;grid-row:1!important}
      body .shell-main.shell-main{grid-column:2!important;grid-row:1!important;padding:18px!important}
      body .sidebar-search-input{flex:1;min-width:0;border:0;outline:none;background:transparent;color:#dbeafe;font:inherit;font-size:12px}
      body .sidebar-search-input::placeholder{color:#6b7fa8}
      body .sidebar-search-clear{width:22px;height:22px;border-radius:7px;border:1px solid rgba(255,255,255,.08);background:rgba(255,255,255,.04);color:#8ea0c3;cursor:pointer}
      body .sidebar-search-clear:hover{color:#fff;background:rgba(244,63,94,.12);border-color:rgba(244,63,94,.28)}
      body .nav-empty.compat-empty{margin:14px;padding:12px;border-radius:12px;color:#8ea0c3;background:rgba(255,255,255,.035);border:1px solid rgba(130,170,255,.1);font-size:12px}
      @media(max-width:760px){body .shell-layout.shell-layout{grid-template-columns:1fr!important}body .shell-sidebar.shell-sidebar{display:none!important}body .shell-main.shell-main{grid-column:1!important;grid-row:1!important;padding:10px!important}}
    `;
    document.head.appendChild(style);
  }

  function textOf(el) {
    return (el.textContent || '').replace(/\s+/g, ' ').trim().toLowerCase();
  }

  function installSidebarSearch() {
    const sidebar = document.querySelector('.shell-sidebar');
    const current = sidebar?.querySelector('.sidebar-search-wrap');
    if (!sidebar || !current || current.dataset.compatSearch === 'true') return;

    const label = document.createElement('label');
    label.className = current.className;
    label.dataset.compatSearch = 'true';
    label.setAttribute('for', 'sidebar-mfe-search');
    label.innerHTML = `
      <svg class="sidebar-search-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="11" cy="11" r="8"></circle><path d="m21 21-4.35-4.35"></path></svg>
      <input id="sidebar-mfe-search" type="search" class="sidebar-search-input" placeholder="Search MFE, group or port…" autocomplete="off">
      <button type="button" class="sidebar-search-clear" aria-label="Clear MFE search" style="display:none">×</button>
      <kbd class="sidebar-search-kbd">⌘K</kbd>
    `;
    current.replaceWith(label);

    const input = label.querySelector('input');
    const clear = label.querySelector('.sidebar-search-clear');
    const kbd = label.querySelector('kbd');
    let empty = sidebar.querySelector('.nav-empty.compat-empty');
    if (!empty) {
      empty = document.createElement('div');
      empty.className = 'nav-empty compat-empty';
      empty.style.display = 'none';
      empty.textContent = 'No micro frontend found.';
      sidebar.querySelector('.remote-nav')?.appendChild(empty);
    }

    function applyFilter() {
      const term = input.value.trim().toLowerCase();
      let totalVisible = 0;
      sidebar.querySelectorAll('.remote-nav .nav-group').forEach(group => {
        let visible = 0;
        group.querySelectorAll('.remote-pill').forEach(button => {
          const port = button.querySelector('.pill-port')?.textContent || '';
          const haystack = `${textOf(button)} ${port}`;
          const match = !term || haystack.includes(term);
          button.style.display = match ? '' : 'none';
          if (match) visible += 1;
        });
        const count = group.querySelector('.nav-group-count');
        if (count) count.textContent = String(visible);
        group.style.display = visible ? '' : 'none';
        totalVisible += visible;
      });
      clear.style.display = term ? '' : 'none';
      if (kbd) kbd.style.display = term ? 'none' : '';
      if (empty) {
        empty.style.display = term && totalVisible === 0 ? '' : 'none';
        empty.textContent = `No micro frontend found for “${input.value}”.`;
      }
    }

    input.addEventListener('input', applyFilter);
    input.addEventListener('click', event => event.stopPropagation());
    input.addEventListener('keydown', event => event.stopPropagation());
    clear.addEventListener('click', event => { event.preventDefault(); input.value = ''; applyFilter(); input.focus(); });
    applyFilter();
  }

  const timer = window.setInterval(installSidebarSearch, 500);
  window.addEventListener('beforeunload', () => window.clearInterval(timer));
  installSidebarSearch();
})();
