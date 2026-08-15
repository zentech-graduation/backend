package com.app.common.security.service.impl;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.app.common.exception.AppException;
import com.app.common.security.jwt.JwtClaims;
import com.app.common.security.jwt.JwtTokenProvider;
import com.app.common.security.service.TokenBlacklistService;
import com.app.common.security.service.TokenPrincipalResolver;
import com.app.common.security.user.SecurityMapper;
import com.app.common.security.user.UserPrincipal;
import com.app.modules.users.enums.UserStatus;
import com.app.modules.users.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TokenPrincipalResolverImpl implements TokenPrincipalResolver {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserRepository userRepository;
    private final SecurityMapper securityMapper;

    public TokenPrincipalResolverImpl(
            JwtTokenProvider jwtTokenProvider,
            TokenBlacklistService tokenBlacklistService,
            UserRepository userRepository,
            SecurityMapper securityMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklistService = tokenBlacklistService;
        this.userRepository = userRepository;
        this.securityMapper = securityMapper;
    }

    @Override
    public Optional<UserPrincipal> resolve(String rawToken) {
        try {
            JwtClaims claims = jwtTokenProvider.validateAndParse(rawToken);
            if (tokenBlacklistService.isBlacklisted(claims.jti())) {
                return Optional.empty();
            }
            return userRepository
                    .findProjectedByIdAndDeletedAtIsNull(claims.userId())
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                    .map(securityMapper::toUserPrincipal);
        } catch (AppException ex) {
            // Every JWT failure (expired, invalid signature, blacklisted lookup) was previously
            // indistinguishable in logs -- this is the only visibility into why auth failed.
            log.debug("JWT authentication rejected: {}", ex.getErrorCode());
            return Optional.empty();
        }
    }
}
