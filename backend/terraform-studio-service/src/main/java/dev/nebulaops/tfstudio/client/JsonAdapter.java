package dev.nebulaops.tfstudio.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonAdapter {
    private final ObjectMapper mapper = new ObjectMapper();

    public Object parse(String value) throws Exception {
        return mapper.readValue(value, Object.class);
    }
}
