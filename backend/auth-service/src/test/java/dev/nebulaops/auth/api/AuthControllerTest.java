package dev.nebulaops.auth.api;

import dev.nebulaops.auth.repo.UserAccountRepository;
import dev.nebulaops.auth.service.JwtService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {
    @Test
    void configuredLocalAdminLoginReturnsBearerTokenShape() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        JwtService jwt = mock(JwtService.class);
        when(users.findByEmail("ops-admin@example.local")).thenReturn(Optional.empty());
        when(jwt.generateAccessToken(any(), any(), any(), any(), any())).thenReturn("access-token");
        when(jwt.generateRefreshToken(any())).thenReturn("refresh-token");

        AuthController controller = new AuthController(users, jwt, true, "ops-admin@example.local", "external-password", "nebulaops");
        Object response = controller.login(new AuthController.LoginRequest("ops-admin@example.local", "external-password")).getBody();

        assertThat(response).asString().contains("access-token").contains("Bearer");
    }
}
