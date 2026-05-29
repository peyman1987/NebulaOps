#!/usr/bin/env bash
# v24.1 extension compile-input guard.
# Fast checks that catch broken Maven POM XML and source-level Java issues before Docker build.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

log_step "Verifying NebulaOps extension Maven/POM inputs"
python3 - <<'PY'
from pathlib import Path
import sys, xml.etree.ElementTree as ET
base = Path('extensions')
errors = []
for pom in sorted(base.glob('*/pom.xml')):
    try:
        root = ET.parse(pom).getroot()
    except Exception as exc:
        errors.append(f'{pom}: invalid XML: {exc}')
        continue
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    artifact = root.findtext('m:artifactId', namespaces=ns) or root.findtext('artifactId')
    name = root.findtext('m:name', namespaces=ns) or root.findtext('name')
    if not artifact:
        errors.append(f'{pom}: missing artifactId')
    if not name:
        errors.append(f'{pom}: missing name')
    text = pom.read_text(encoding='utf-8')
    if '<name>' in text and ' & ' in text and '&amp;' not in text:
        errors.append(f'{pom}: raw ampersand in <name>; use &amp;')
if errors:
    for e in errors: print('ERROR:', e, file=sys.stderr)
    sys.exit(1)
print('OK: extension POM XML files are parseable')
PY

missing=0
for ext in extensions/*; do
  [ -d "$ext" ] || continue
  name="$(basename "$ext")"
  [ "$name" = "node_modules" ] && continue
  for required in pom.xml Dockerfile k8s/deployment.yml; do
    if [ ! -f "$ext/$required" ]; then
      log_err "$name missing $required"
      missing=1
    fi
  done
done
[ "$missing" -eq 0 ] || exit 1

# Optional source-level guard. It does not replace Maven, but it catches missing methods like
# service.readiness(), service.capabilities() and summarize* references before Docker build.
if command -v javac >/dev/null 2>&1; then
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  mkdir -p "$tmp/stubs/org/springframework/beans/factory/annotation" \
           "$tmp/stubs/org/springframework/boot/autoconfigure" \
           "$tmp/stubs/org/springframework/boot" \
           "$tmp/stubs/org/springframework/core/env" \
           "$tmp/stubs/org/springframework/http" \
           "$tmp/stubs/org/springframework/stereotype" \
           "$tmp/stubs/org/springframework/ui" \
           "$tmp/stubs/org/springframework/web/bind/annotation" \
           "$tmp/stubs/com/fasterxml/jackson/databind"
  cat > "$tmp/stubs/org/springframework/beans/factory/annotation/Value.java" <<'JAVA'
package org.springframework.beans.factory.annotation; public @interface Value { String value(); }
JAVA
  cat > "$tmp/stubs/org/springframework/boot/autoconfigure/SpringBootApplication.java" <<'JAVA'
package org.springframework.boot.autoconfigure; public @interface SpringBootApplication {}
JAVA
  cat > "$tmp/stubs/org/springframework/boot/SpringApplication.java" <<'JAVA'
package org.springframework.boot; public class SpringApplication { public static Object run(Class<?> c, String[] args){ return null; } }
JAVA
  cat > "$tmp/stubs/org/springframework/core/env/Environment.java" <<'JAVA'
package org.springframework.core.env; public interface Environment { String getProperty(String key); String getProperty(String key, String defaultValue); }
JAVA
  cat > "$tmp/stubs/org/springframework/http/MediaType.java" <<'JAVA'
package org.springframework.http; public class MediaType { public static final String APPLICATION_JSON_VALUE="application/json"; }
JAVA
  cat > "$tmp/stubs/org/springframework/stereotype/Controller.java" <<'JAVA'
package org.springframework.stereotype; public @interface Controller {}
JAVA
  cat > "$tmp/stubs/org/springframework/stereotype/Service.java" <<'JAVA'
package org.springframework.stereotype; public @interface Service {}
JAVA
  cat > "$tmp/stubs/org/springframework/ui/Model.java" <<'JAVA'
package org.springframework.ui; public interface Model { Model addAttribute(String key, Object value); }
JAVA
  cat > "$tmp/stubs/org/springframework/web/bind/annotation/GetMapping.java" <<'JAVA'
package org.springframework.web.bind.annotation; public @interface GetMapping { String[] value() default {}; }
JAVA
  cat > "$tmp/stubs/org/springframework/web/bind/annotation/PostMapping.java" <<'JAVA'
package org.springframework.web.bind.annotation; public @interface PostMapping { String[] value() default {}; String consumes() default ""; }
JAVA
  cat > "$tmp/stubs/org/springframework/web/bind/annotation/RequestBody.java" <<'JAVA'
package org.springframework.web.bind.annotation; public @interface RequestBody {}
JAVA
  cat > "$tmp/stubs/org/springframework/web/bind/annotation/RequestParam.java" <<'JAVA'
package org.springframework.web.bind.annotation; public @interface RequestParam { String value() default ""; boolean required() default true; String defaultValue() default ""; }
JAVA
  cat > "$tmp/stubs/org/springframework/web/bind/annotation/ResponseBody.java" <<'JAVA'
package org.springframework.web.bind.annotation; public @interface ResponseBody {}
JAVA
  cat > "$tmp/stubs/org/springframework/web/bind/annotation/RestController.java" <<'JAVA'
package org.springframework.web.bind.annotation; public @interface RestController {}
JAVA
  cat > "$tmp/stubs/com/fasterxml/jackson/databind/JsonNode.java" <<'JAVA'
package com.fasterxml.jackson.databind;
import java.util.*;
public class JsonNode implements Iterable<JsonNode> {
 public JsonNode path(String field){ return this; }
 public boolean asBoolean(boolean d){ return d; }
 public int asInt(int d){ return d; }
 public String asText(){ return ""; }
 public String asText(String d){ return d; }
 public boolean isMissingNode(){ return false; }
 public boolean isNull(){ return false; }
 public Iterator<String> fieldNames(){ return Collections.<String>emptyList().iterator(); }
 public JsonNode get(String k){ return this; }
 public boolean isContainerNode(){ return false; }
 public boolean isArray(){ return false; }
 public boolean isObject(){ return false; }
 public int size(){ return 0; }
 public Iterator<JsonNode> iterator(){ return Collections.<JsonNode>emptyList().iterator(); }
}
JAVA
  cat > "$tmp/stubs/com/fasterxml/jackson/databind/ObjectMapper.java" <<'JAVA'
package com.fasterxml.jackson.databind; public class ObjectMapper { public JsonNode readTree(String s){ return new JsonNode(); } }
JAVA
  javac -d "$tmp/stubs-classes" $(find "$tmp/stubs" -name '*.java')
  for src in extensions/*/src/main/java/dev/nebulaops/extensions/*Application.java; do
    [ -f "$src" ] || continue
    out="$tmp/classes/$(basename "$(dirname "$(dirname "$(dirname "$src")")")")"
    mkdir -p "$out"
    if ! javac -Xlint:none --release 21 -cp "$tmp/stubs-classes" -d "$out" "$src" >"$tmp/javac.log" 2>&1; then
      cat "$tmp/javac.log" >&2
      exit 1
    fi
  done
  log_ok "extension Spring MVC source-level guard passed"
else
  log_warn "javac not found: skipping optional extension source-level guard"
fi

log_ok "NebulaOps extension compile inputs verified"
