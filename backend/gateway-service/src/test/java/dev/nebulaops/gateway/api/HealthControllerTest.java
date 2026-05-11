package dev.nebulaops.gateway.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {
    @Test
    void healthReportsVersion232() {
        HealthController controller = new HealthController();
        assertThat(controller.health()).containsEntry("version", "23.4");
        assertThat(controller.ping()).containsEntry("release", "NebulaOps v23.4");
    }
}
