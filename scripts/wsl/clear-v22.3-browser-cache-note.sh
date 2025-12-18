#!/usr/bin/env bash
cat <<'MSG'
Browser cache cleanup for NebulaOps v22.3:

1. Open Chrome DevTools on nebulaops.localhost.
2. Application > Storage > Clear site data.
3. Repeat once for nebulaops.localhost/remotes/docker-desktop if standalone Docker Desktop was already open.
4. Reopen:
   http://nebulaops.localhost/?v=v22.3.6-live-real-data
5. Use Ctrl+Shift+R.

This package serves index.html, remoteEntry.js and nebulaops-auth-bridge.js with no-store headers, so the cleanup is only needed for old tabs created before this fix.
MSG
