package dev.nebulaops.shared;

import java.time.Instant;
import java.util.List;

public record PolicyEvaluation(String id, String target, String status, List<String> results, Instant evaluatedAt) {}
