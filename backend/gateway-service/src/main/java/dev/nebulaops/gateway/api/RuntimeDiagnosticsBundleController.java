package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.service.RuntimeDiagnosticsBundleService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * NebulaOps v24.1 — downloadable live diagnostics bundle for support/debug.
 */
@RestController
public class RuntimeDiagnosticsBundleController {
    private final RuntimeDiagnosticsBundleService bundleService;

    public RuntimeDiagnosticsBundleController(RuntimeDiagnosticsBundleService bundleService) {
        this.bundleService = bundleService;
    }

    @GetMapping("/api/runtime/diagnostics/bundle")
    public Map<String, Object> bundle() {
        return bundleService.bundle();
    }

    @GetMapping(value = "/api/runtime/diagnostics/bundle.zip", produces = "application/zip")
    public ResponseEntity<byte[]> bundleZip() {
        byte[] body = bundleService.bundleZip();
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "").replace(".", "-");
        String filename = "nebulaops-v24.1-diagnostics-bundle-" + timestamp + ".zip";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentLength(body.length);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
