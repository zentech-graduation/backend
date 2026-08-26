package com.app.common.seed.model;

public record UserSeed(
        String username,
        String displayName,
        String email,
        String bio,
        String personaId,
        String role,
        String status,
        boolean isPrivate,
        boolean emailVerified,
        int createdAtOffsetDays,
        String avatarUrl,
        String bannerMediaRef,
        int postCountTarget,
        boolean isQaAccount,
        String qaNote) {}
