<#--
  NebulaOps v22.2 — Keycloak Custom Login Template
  Target : Keycloak 24.0.4 — FreeMarker with HTMLOutputFormat (auto-escaping ON)
  
  CRITICAL: In KC 24, FreeMarker runs with HTMLOutputFormat enabled.
  This means:
    - ALL dollar-brace output is auto-escaped — do NOT use the html built-in (ParseException!)
    - To output trusted/raw HTML use ?no_esc
    - Plain string values: just use dollar-brace variable — auto-escaped automatically
-->

<#-- ── Safe variable defaults ─────────────────────────────────────────── -->
<#assign nbLoginAction="">
<#assign nbUsername="">
<#assign nbRemember=false>
<#assign nbForgot=false>
<#assign nbShowUsername=true>
<#assign nbMessageType="info">
<#assign nbMessageSummary="">
<#assign nbSelectedCredential="">

<#if url?? && url.loginAction??>
  <#assign nbLoginAction=url.loginAction>
</#if>
<#if login?? && login.username??>
  <#assign nbUsername=login.username>
</#if>
<#if realm?? && realm.rememberMe?? && realm.rememberMe>
  <#assign nbRemember=true>
</#if>
<#if realm?? && realm.resetPasswordAllowed?? && realm.resetPasswordAllowed && url?? && url.loginResetCredentialsUrl??>
  <#assign nbForgot=true>
</#if>
<#if usernameHidden?? && usernameHidden>
  <#assign nbShowUsername=false>
</#if>
<#if message?? && message.type??>
  <#assign nbMessageType=message.type>
</#if>
<#if message?? && message.summary??>
  <#assign nbMessageSummary=message.summary>
</#if>
<#if auth?? && auth.selectedCredential??>
  <#assign nbSelectedCredential=auth.selectedCredential>
</#if>

<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="robots" content="noindex, nofollow">
  <title>NebulaOps v22.2 · Sign In</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800;900&display=swap" rel="stylesheet">
  <style>
    /* ── Design tokens — mirrors frontend/src/styles.css ──────────────── */
    :root {
      --bg-deep:       #020617;
      --bg-base:       #07111f;
      --bg-mid:        #0f1028;
      --bg-card:       rgba(10,20,45,0.80);
      --bg-glass:      rgba(15,23,52,0.62);
      --fg-primary:    #f8fafc;
      --fg-soft:       #e5f2ff;
      --fg-muted:      #c6d3e5;
      --fg-dim:        #94a3b8;
      --fg-faint:      #64748b;
      --accent-cyan:   #22d3ee;
      --accent-blue:   #38bdf8;
      --accent-violet: #8b5cf6;
      --grad-primary:  linear-gradient(135deg,#22d3ee 0%,#8b5cf6 100%);
      --grad-bg-app:
        radial-gradient(ellipse at 6%   0%,  rgba(56,189,248,.16),transparent 30%),
        radial-gradient(ellipse at 88%  5%,  rgba(168,85,247,.18),transparent 32%),
        radial-gradient(ellipse at 50% 100%, rgba(20,184,166,.08),transparent 40%),
        linear-gradient(160deg,#020617 0%,#07111f 48%,#0f1028 100%);
      --border-faint:  rgba(186,230,253,.08);
      --border-soft:   rgba(186,230,253,.16);
      --border-mid:    rgba(186,230,253,.26);
      --radius-sm:     12px;
      --radius-md:     18px;
      --radius-xl:     32px;
      --radius-pill:   999px;
      --shadow-card:   0 8px 32px rgba(0,0,0,.36),inset 0 1px 0 rgba(255,255,255,.04);
      --shadow-lg:     0 28px 80px rgba(0,0,0,.55);
      --shadow-glow:   0 0 36px rgba(34,211,238,.26);
      --shadow-violet: 0 0 36px rgba(139,92,246,.28);
      --motion-fast:   0.18s ease;
      --font-sans:     'Inter',ui-sans-serif,system-ui,'Segoe UI',Arial,sans-serif;
    }

    *,*::before,*::after{box-sizing:border-box;margin:0;padding:0;}
    ::selection{background:rgba(34,211,238,.30);color:#fff;}

    html,body{
      min-height:100vh;
      font-family:var(--font-sans);
      color:var(--fg-soft);
      background:var(--grad-bg-app);
      background-attachment:fixed;
      -webkit-font-smoothing:antialiased;
      display:flex;
      align-items:center;
      justify-content:center;
      padding:24px;
      position:relative;
      overflow:hidden;
    }

    body::before{
      content:'';
      position:fixed;
      inset:0;
      background-image:
        linear-gradient(rgba(125,211,252,.045) 1px,transparent 1px),
        linear-gradient(90deg,rgba(125,211,252,.045) 1px,transparent 1px);
      background-size:70px 70px;
      pointer-events:none;
      z-index:0;
    }

    .nb-card{
      position:relative;
      z-index:1;
      width:min(468px,94vw);
      padding:40px 44px 36px;
      border:1px solid var(--border-soft);
      border-radius:var(--radius-xl);
      background:var(--bg-card);
      box-shadow:var(--shadow-lg),var(--shadow-card);
      backdrop-filter:blur(28px) saturate(140%);
      -webkit-backdrop-filter:blur(28px) saturate(140%);
    }

    .nb-card::before{
      content:'';
      position:absolute;
      top:0;left:50%;
      transform:translateX(-50%);
      width:55%;height:1px;
      background:var(--grad-primary);
      border-radius:var(--radius-pill);
      opacity:.55;
    }

    .nb-brand{display:flex;align-items:center;gap:14px;margin-bottom:6px;}

    .nb-logo{
      display:grid;place-items:center;
      width:52px;height:52px;
      border-radius:var(--radius-md);
      background:var(--grad-primary);
      font-size:11px;font-weight:900;color:#fff;
      box-shadow:var(--shadow-glow),var(--shadow-violet);
      flex-shrink:0;user-select:none;
    }

    .nb-brand-eyebrow{
      color:var(--accent-cyan);
      font-size:10px;font-weight:800;
      letter-spacing:.18em;text-transform:uppercase;
      margin-bottom:3px;
    }

    .nb-brand-name{
      color:var(--fg-primary);
      font-size:20px;font-weight:900;letter-spacing:-.03em;
    }

    .nb-lead{
      color:var(--fg-dim);font-size:12px;
      line-height:1.55;margin-top:3px;margin-bottom:22px;
    }

    .nb-divider{
      height:1px;background:var(--border-faint);
      margin:0 -44px 24px;
    }

    .nb-section-label{
      color:var(--fg-muted);font-size:12px;font-weight:700;
      letter-spacing:.08em;text-transform:uppercase;margin-bottom:20px;
    }

    /* Alerts — 4 variants keyed by Keycloak's message.type enum */
    .nb-alert{
      padding:11px 15px;border-radius:var(--radius-sm);
      font-size:13px;font-weight:600;line-height:1.45;margin-bottom:20px;
    }
    .nb-alert-error  {border:1px solid rgba(244,63,94,.35); background:rgba(127,29,29,.22); color:#fca5a5;}
    .nb-alert-success{border:1px solid rgba(16,185,129,.30);background:rgba(6,78,59,.22);   color:#6ee7b7;}
    .nb-alert-warning{border:1px solid rgba(245,158,11,.30);background:rgba(120,53,15,.25); color:#fde68a;}
    .nb-alert-info   {border:1px solid rgba(56,189,248,.28);background:rgba(12,74,110,.22); color:#bae6fd;}

    .nb-field{margin-bottom:16px;}

    .nb-field label{
      display:block;margin-bottom:7px;
      color:var(--fg-muted);font-size:11px;font-weight:700;
      letter-spacing:.06em;text-transform:uppercase;
    }

    .nb-field input[type="text"],
    .nb-field input[type="password"]{
      width:100%;padding:12px 15px;
      border-radius:var(--radius-sm);
      border:1px solid var(--border-soft);
      background:var(--bg-glass);
      color:var(--fg-primary);
      font-family:var(--font-sans);font-size:14px;font-weight:500;
      outline:none;
      transition:border-color var(--motion-fast),box-shadow var(--motion-fast);
    }
    .nb-field input:hover{border-color:var(--border-mid);}
    .nb-field input:focus{
      border-color:var(--accent-cyan);
      box-shadow:0 0 0 3px rgba(34,211,238,.18);
    }
    .nb-field input::placeholder{color:var(--fg-faint);}

    .nb-form-options{
      display:flex;align-items:center;justify-content:space-between;
      gap:12px;margin:6px 0 22px;
    }

    .nb-checkbox{
      display:flex;align-items:center;gap:7px;
      color:var(--fg-dim);font-size:12px;font-weight:600;
      cursor:pointer;user-select:none;
    }
    .nb-checkbox input[type="checkbox"]{
      width:15px;height:15px;border-radius:4px;
      accent-color:var(--accent-cyan);cursor:pointer;flex-shrink:0;
    }

    .nb-forgot{
      color:var(--accent-blue);font-size:12px;font-weight:700;
      text-decoration:none;transition:color var(--motion-fast);
    }
    .nb-forgot:hover{color:var(--accent-cyan);}

    .nb-btn-primary{
      width:100%;padding:14px 24px;border:none;
      border-radius:var(--radius-pill);
      background:var(--grad-primary);
      color:#fff;font-family:var(--font-sans);
      font-size:14px;font-weight:800;letter-spacing:.02em;
      cursor:pointer;
      box-shadow:0 8px 32px rgba(34,211,238,.22),0 2px 8px rgba(0,0,0,.30);
      transition:filter var(--motion-fast),transform var(--motion-fast),box-shadow var(--motion-fast);
    }
    .nb-btn-primary:hover:not(:disabled){
      filter:brightness(1.12);transform:translateY(-1px);
      box-shadow:0 12px 40px rgba(34,211,238,.32),0 4px 12px rgba(0,0,0,.30);
    }
    .nb-btn-primary:active:not(:disabled){transform:translateY(0);filter:brightness(.96);}
    .nb-btn-primary:disabled{opacity:.50;cursor:not-allowed;transform:none;}

    .nb-footer{
      margin-top:24px;padding-top:16px;
      border-top:1px solid var(--border-faint);
      color:var(--fg-faint);font-size:10px;font-weight:700;
      text-align:center;letter-spacing:.10em;text-transform:uppercase;
    }

    @media(max-width:520px){
      .nb-card{padding:30px 22px 26px;}
      .nb-divider{margin-left:-22px;margin-right:-22px;}
    }
    @media(prefers-reduced-motion:reduce){
      *,*::before,*::after{animation-duration:.01ms!important;transition-duration:.01ms!important;}
    }
  </style>
</head>
<body>
  <main class="nb-card" role="main">

    <div class="nb-brand">
      <div class="nb-logo" aria-hidden="true">N22.2</div>
      <div>
        <div class="nb-brand-eyebrow">Terraform-enabled SaaS cockpit</div>
        <div class="nb-brand-name">NebulaOps v22.2</div>
      </div>
    </div>
    <p class="nb-lead">Docker &middot; Kubernetes &middot; Helm &middot; Terraform &middot; GitOps &middot; Observability</p>

    <div class="nb-divider" role="separator"></div>
    <div class="nb-section-label">Sign in to your workspace</div>

    <#--
      ALERT BLOCK
      - nbMessageType: bare ${} — auto-escaped by HTMLOutputFormat, safe in class attr
      - nbMessageSummary: bare ${} — auto-escaped; KC may send plain text or pre-escaped HTML
        If KC sends HTML tags in summary, use ?no_esc. For KC 24 plain-text messages, bare is correct.
    -->
    <#if nbMessageSummary?has_content>
      <div class="nb-alert nb-alert-${nbMessageType}" role="alert">
        ${nbMessageSummary}
      </div>
    </#if>

    <form id="kc-form-login"
          onsubmit="document.getElementById('kc-login').disabled=true;return true;"
          action="${nbLoginAction}"
          method="post">

      <#if nbShowUsername>
        <div class="nb-field">
          <label for="username">Username or email</label>
          <input tabindex="1"
                 id="username"
                 name="username"
                 value="${nbUsername}"
                 type="text"
                 autofocus
                 autocomplete="username"
                 placeholder="admin">
        </div>
      </#if>

      <div class="nb-field">
        <label for="password">Password</label>
        <input tabindex="2"
               id="password"
               name="password"
               type="password"
               autocomplete="current-password"
               placeholder="••••••••">
      </div>

      <div class="nb-form-options">
        <#if nbRemember && nbShowUsername>
          <label class="nb-checkbox" for="rememberMe">
            <input tabindex="3"
                   id="rememberMe"
                   name="rememberMe"
                   type="checkbox"
                   <#if login?? && login.rememberMe??>checked</#if>>
            <span>Remember me</span>
          </label>
        <#else>
          <span></span>
        </#if>

        <#if nbForgot>
          <a tabindex="5"
             href="${url.loginResetCredentialsUrl}"
             class="nb-forgot">Forgot password?</a>
        </#if>
      </div>

      <input type="hidden"
             id="id-hidden-input"
             name="credentialId"
             value="${nbSelectedCredential}">

      <button tabindex="4"
              class="nb-btn-primary"
              name="login"
              id="kc-login"
              type="submit">Sign in</button>
    </form>

    <div class="nb-footer">DevOps Enterprise Cockpit &middot; v22.2 &middot; Local-first</div>
  </main>
</body>
</html>
