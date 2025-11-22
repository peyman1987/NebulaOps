package dev.nebulaops.shared.runtime;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class RestPathBuilder {
    private RestPathBuilder() {
    }

    public static String api(String... parts) {
        String suffix = Arrays.stream(parts)
                .filter(part -> part != null && !part.isBlank())
                .map(part -> part.replaceAll("^/+|/+$", ""))
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining("/"));
        return suffix.isBlank() ? "/api" : "/api/" + suffix;
    }
}
