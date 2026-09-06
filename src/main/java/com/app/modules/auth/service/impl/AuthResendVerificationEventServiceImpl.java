package com.app.modules.auth.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.modules.auth.entity.UserCredential;
import com.app.modules.auth.repository.UserCredentialRepository;
import com.app.modules.auth.service.AuthMailEventService;
import com.app.modules.auth.service.AuthResendVerificationEventService;
import com.app.modules.users.entity.User;
import com.app.modules.users.repository.UserRepository;

@Service
public class AuthResendVerificationEventServiceImpl implements AuthResendVerificationEventService {

    private final UserRepository userRepository;
    private final UserCredentialRepository credentialRepository;
    private final AuthMailEventService authMailEventService;

    public AuthResendVerificationEventServiceImpl(
            UserRepository userRepository,
            UserCredentialRepository credentialRepository,
            AuthMailEventService authMailEventService) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.authMailEventService = authMailEventService;
    }

    @Override
    @Transactional
    public void recordResendVerificationRequest(String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);
        if (user == null) {
            return;
        }
        UserCredential credential = credentialRepository.findByUserId(user.getId()).orElse(null);
        if (credential != null && credential.isEmailVerified()) {
            return;
        }

        authMailEventService.publishEmailVerificationRequested(user, null);
    }
}
