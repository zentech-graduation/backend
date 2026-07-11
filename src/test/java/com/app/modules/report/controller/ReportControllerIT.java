package com.app.modules.report.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.app.common.exception.GlobalExceptionHandler;
import com.app.common.response.PageResponse;
import com.app.common.security.UserPrincipal;
import com.app.modules.report.dto.response.ReportResponse;
import com.app.modules.report.enums.ReportReason;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;
import com.app.modules.report.service.ReportService;

@WebMvcTest(useDefaultFilters = false)
@Import({
    ReportController.class,
    GlobalExceptionHandler.class,
    ReportControllerIT.TestSecurity.class
})
@ContextConfiguration(
        classes = {
            ReportController.class,
            GlobalExceptionHandler.class,
            ReportControllerIT.TestSecurity.class
        })
class ReportControllerIT {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ReportService reportService;

    @Test
    void submit_authenticatedUser_returnsCreated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        when(reportService.submit(eq(userId), any()))
                .thenReturn(response(reportId, userId, entityId));

        mockMvc.perform(
                        post("/api/v1/reports")
                                .with(authentication(auth(userId, "USER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
										{"reportType":"POST","reportReason":"SPAM","entityId":"%s"}
										"""
                                                .formatted(entityId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(reportId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void list_regularUser_returnsForbidden() throws Exception {
        mockMvc.perform(
                        get("/api/v1/reports")
                                .with(authentication(auth(UUID.randomUUID(), "USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_moderator_returnsOk() throws Exception {
        when(reportService.findAll(any(), any(), any()))
                .thenReturn(
                        PageResponse.<ReportResponse>builder()
                                .content(List.of())
                                .page(0)
                                .size(20)
                                .totalElements(0)
                                .totalPages(0)
                                .first(true)
                                .last(true)
                                .empty(true)
                                .build());

        mockMvc.perform(
                        get("/api/v1/reports")
                                .with(authentication(auth(UUID.randomUUID(), "MODERATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    private static ReportResponse response(UUID id, UUID reporterId, UUID entityId) {
        return new ReportResponse(
                id,
                reporterId,
                ReportType.POST,
                ReportReason.SPAM,
                entityId,
                null,
                ReportStatus.PENDING,
                null,
                null,
                null,
                null);
    }

    private static Authentication auth(UUID userId, String role) {
        UserPrincipal principal = new UserPrincipal(userId, "test@example.com", role, "ACTIVE");
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @TestConfiguration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class TestSecurity {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(request -> request.anyRequest().authenticated())
                    .build();
        }
    }
}
