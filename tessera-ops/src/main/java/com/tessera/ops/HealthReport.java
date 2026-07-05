package com.tessera.ops;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record HealthReport(String status, OffsetDateTime checkedAt, List<Check> checks, Map<String, Object> meta) {

    public boolean healthy() {
        return "UP".equals(status);
    }

    public record Check(String name, String status, String message, long latencyMs) {}
}
