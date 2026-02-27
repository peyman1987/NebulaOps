package dev.nebulaops.aiops;

import dev.nebulaops.aiops.service.AiOpsService;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiOpsControllerTest {
    @Test
    void analyzeFallsBackWhenAiEngineUnavailable() {
        AiOpsService service = mock(AiOpsService.class);
        when(service.analyze(anyMap())).thenReturn(Map.of("live", false, "rootCause", "fallback"));
        RestTemplate rest = new RestTemplate();
        AiOpsController controller = new AiOpsController(service, rest,
                "http://127.0.0.1:9", "http://127.0.0.1:9", "http://127.0.0.1:9",
                "http://127.0.0.1:9", "http://127.0.0.1:9", "http://127.0.0.1:9");

        Map<String, Object> result = controller.analyze(Map.of("affectedService", "gateway-service"));

        assertThat(result).containsKey("ai").containsKey("fallback").containsEntry("live", true);
    }
}
