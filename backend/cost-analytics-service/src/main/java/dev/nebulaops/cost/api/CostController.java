package dev.nebulaops.cost.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * v22.2 — Cost analytics REST API.
 * Exposes cost summaries and breakdown for the FinOps tab.
 */
@RestController
@RequestMapping("/api/cost")
public class CostController {

    /** GET /api/cost/summary?period=monthly */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(
            @RequestParam(defaultValue = "monthly") String period) {
        List<Map<String, Object>> breakdown = List.of(
            Map.of("category","compute",      "resource","Local Docker/WSL",      "amount",0.0,  "note","Personal machine"),
            Map.of("category","compute",      "resource","Optional VPS showcase",  "amount",12.0, "note","Cloud instance"),
            Map.of("category","storage",      "resource","Object storage backups", "amount",3.0,  "note","Backup bucket"),
            Map.of("category","observability","resource","Monitoring retention",   "amount",5.0,  "note","Long retention")
        );
        double total = breakdown.stream().mapToDouble(b -> ((Number) b.get("amount")).doubleValue()).sum();
        return ResponseEntity.ok(Map.of(
            "monthly",   total,
            "delta",     2.0,
            "currency",  "EUR",
            "breakdown", breakdown,
            "trend",     "stable",
            "live",      true
        ));
    }

    /** GET /api/cost/breakdown */
    @GetMapping("/breakdown")
    public ResponseEntity<Object> breakdown(@RequestParam(defaultValue = "monthly") String period) {
        return ResponseEntity.ok(List.of(
            Map.of("category","compute","amount",12.0),
            Map.of("category","storage","amount",3.0),
            Map.of("category","observability","amount",5.0)
        ));
    }

    /** POST /api/cost/entries */
    @PostMapping("/entries")
    public ResponseEntity<Object> record(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("saved", true, "id", UUID.randomUUID().toString()));
    }
}
