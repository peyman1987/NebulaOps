package dev.nebulaops.gateway.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {
    @Test
    void healthReportsVersion225() {
        HealthController controller = new HealthController();
        assertThat(controller.health()).containsEntry("version", "22.5");
        assertThat(controller.ping()).containsEntry("release", "NebulaOps v22.5");
    }
}
