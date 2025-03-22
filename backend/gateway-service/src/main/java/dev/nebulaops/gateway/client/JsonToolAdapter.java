package dev.nebulaops.gateway.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class JsonToolAdapter {
    private final ObjectMapper mapper = new ObjectMapper();

    public Object parseJson(String json) throws Exception {
        return mapper.readValue(json, Object.class);
    }

    public List<Map<String, Object>> parseJsonLines(String text) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (text == null || text.isBlank()) return rows;
        for (String line : text.split("\\R")) {
            if (line.isBlank()) continue;
            try {
                rows.add(mapper.readValue(line, new TypeReference<>() {
                }));
            } catch (Exception ignored) {
            }
        }
        return rows;
    }

    public Map<String, Object> parseMap(String json) throws Exception {
        return mapper.readValue(json, new TypeReference<>() {
        });
    }
}
