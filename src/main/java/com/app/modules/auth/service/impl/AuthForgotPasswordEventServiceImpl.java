package com.app.modules.auth.service.impl;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.modules.auth.entity.User;
import com.app.modules.auth.entity.UserCredential;
import com.app.modules.auth.enums.UserStatus;
import com.app.modules.auth.repository.UserCredentialRepository;
import com.app.modules.auth.repository.UserRepository;
import com.app.modules.auth.service.AuthForgotPasswordEventService;
import com.app.modules.auth.service.AuthMailEventService;

@Service
public class AuthForgotPasswordEventServiceImpl implements AuthForgotPasswordEventService {

    private final UserRepository userRepository;
    private final UserCredentialRepository credentialRepository;
    private final AuthMailEventService authMailEventService;

    public AuthForgotPasswordEventServiceImpl(
            UserRepository userRepository,
            UserCredentialRepository credentialRepository,
            AuthMailEventService authMailEventService) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.authMailEventService = authMailEventService;
    }

    @Override
    @Transactional
    public void recordForgotPasswordRequest(String email) {
        Optional<User> userOpt = userRepository.findByEmailAndDeletedAtIsNull(email);
        if (userOpt.isEmpty()) {
            return;
        }
        User user = userOpt.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            return;
        }

        Optional<UserCredential> cred = credentialRepository.findByUserId(user.getId());
        if (cred.isEmpty() || cred.get().getPasswordHash() == null) {
            authMailEventService.publishOAuthAccountNoPassword(user, null);
            return;
        }

        authMailEventService.publishPasswordResetRequested(user, null);
    }
}
