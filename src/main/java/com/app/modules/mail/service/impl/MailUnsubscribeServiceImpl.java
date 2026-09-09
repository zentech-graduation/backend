package com.app.modules.mail.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.mail.service.MailUnsubscribeService;
import com.app.modules.users.entity.UserSettings;
import com.app.modules.users.repository.UserSettingsRepository;

@Service
public class MailUnsubscribeServiceImpl implements MailUnsubscribeService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserSettingsRepository userSettingsRepository;

    public MailUnsubscribeServiceImpl(UserSettingsRepository userSettingsRepository) {
        this.userSettingsRepository = userSettingsRepository;
    }

    @Override
    @Transactional
    public String tokenFor(UUID userId) {
        UserSettings settings =
                userSettingsRepository
                        .findById(userId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.USER_NOT_FOUND));
        // Minted lazily and then kept. Regenerating per mail would break every link already sitting
        // in a recipient's inbox, which is exactly the failure a stored secret avoids.
        byte[] buf = new byte[32];
        SECURE_RANDOM.nextBytes(buf);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        if (settings.getUnsubscribeToken() == null) {
            settings.setUnsubscribeToken(sha256(rawToken));
            userSettingsRepository.save(settings);
            return rawToken;
        }
        // An existing token cannot be recovered from its hash, so a row that already has one gets a
        // fresh secret. The previous link stops working, which is acceptable: the newest mail
        // always
        // carries a working link, and the account can also opt out from its settings.
        settings.setUnsubscribeToken(sha256(rawToken));
        userSettingsRepository.save(settings);
        return rawToken;
    }

    @Override
    @Transactional
    public void unsubscribe(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AppException(ApiErrorCode.UNSUBSCRIBE_TOKEN_INVALID);
        }
        UserSettings settings =
                userSettingsRepository
                        .findByUnsubscribeToken(sha256(rawToken))
                        .orElseThrow(
                                () -> new AppException(ApiErrorCode.UNSUBSCRIBE_TOKEN_INVALID));
        // The token is deliberately not consumed. A mail client prefetching the link, or a
        // recipient
        // clicking twice, must not be told the link is broken for something that already worked.
        settings.setEmailOptOut(true);
        userSettingsRepository.save(settings);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
