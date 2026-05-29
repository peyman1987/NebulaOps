package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.config.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v24.1 API guard: API requests must fail with JSON contracts, not HTML error
 * pages. This keeps the shell and MFE remotes in explicit DEGRADED/ERROR states
 * when a backend path fails during runtime.
 */
@RestControllerAdvice
public class GlobalApiExceptionHandler {

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> internalError(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", ex, request);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, Exception ex, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("live", false);
        body.put("state", "ERROR");
        body.put("source", "gateway");
        body.put("code", code);
        body.put("message", safeMessage(ex));
        body.put("path", request == null ? "" : request.getRequestURI());
        body.put("correlationId", correlationId(request));
        body.put("generatedAt", Instant.now().toString());
        body.put("realDataOnly", true);
        return ResponseEntity.status(status).body(body);
    }

    private String correlationId(HttpServletRequest request) {
        String mdc = MDC.get("correlationId");
        if (mdc != null && !mdc.isBlank()) return mdc;
        String header = request == null ? null : request.getHeader(CorrelationIdFilter.HEADER);
        return header == null || header.isBlank() ? "unavailable" : header;
    }

    private String safeMessage(Exception ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) return "Runtime request failed";
        String message = ex.getMessage();
        return message.length() > 500 ? message.substring(0, 500) + "..." : message;
    }
}
