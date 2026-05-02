package com.app.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
                @Size(min = 3, max = 30)
                @Pattern(
                        regexp = "^[a-zA-Z0-9_.]+$",
                        message = "Username may only contain letters, digits, underscores and dots")
                String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @Size(max = 100) String displayName) {}
