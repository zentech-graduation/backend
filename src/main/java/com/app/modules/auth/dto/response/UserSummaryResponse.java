package com.app.modules.auth.dto.response;

import java.util.UUID;

public record UserSummaryResponse(
        UUID id,
        String username,
        String email,
        String displayName,
        String role,
        boolean emailVerified) {}
