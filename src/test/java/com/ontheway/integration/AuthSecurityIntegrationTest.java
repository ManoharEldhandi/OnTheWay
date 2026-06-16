package com.ontheway.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontheway.dto.LoginRequest;
import com.ontheway.dto.UserCreateDTO;
import com.ontheway.model.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end HTTP tests for authentication and the JWT security layer.
 * Proves the Phase 0 security fixes through the full filter chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    private void register(String email, UserRole role) throws Exception {
        UserCreateDTO dto = UserCreateDTO.builder()
                .email(email).password("password123").name("Test").role(role).build();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json(dto)))
                .andExpect(status().isCreated());
    }

    @Test
    void register_thenLogin_returnsToken() throws Exception {
        register("login-flow@x.com", UserRole.USER);
        LoginRequest login = LoginRequest.builder()
                .email("login-flow@x.com").password("password123").build();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(json(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void register_asAdmin_isRejectedWith400() throws Exception {
        UserCreateDTO dto = UserCreateDTO.builder()
                .email("admin-attempt@x.com").password("password123").name("X").role(UserRole.ADMIN).build();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_duplicateEmail_isRejectedWith409() throws Exception {
        register("dup-int@x.com", UserRole.USER);
        UserCreateDTO dto = UserCreateDTO.builder()
                .email("dup-int@x.com").password("password123").name("X").role(UserRole.USER).build();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_shortPassword_isRejectedWith400() throws Exception {
        UserCreateDTO dto = UserCreateDTO.builder()
                .email("shortpw@x.com").password("123").name("X").role(UserRole.USER).build();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_wrongPassword_isUnauthorized() throws Exception {
        register("wrongpw@x.com", UserRole.USER);
        LoginRequest login = LoginRequest.builder()
                .email("wrongpw@x.com").password("not-the-password").build();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(json(login)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withGarbageToken_returns401NotServerError() throws Exception {
        // Regression: a malformed/expired token must yield 401, never a 500.
        mockMvc.perform(get("/api/orders/1")
                        .header("Authorization", "Bearer not.a.real.jwt"))
                .andExpect(status().isUnauthorized());
    }
}
