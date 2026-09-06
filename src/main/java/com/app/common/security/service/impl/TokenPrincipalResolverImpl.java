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
import com.app.modules.users.repository.UserSecurityProjection;

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
            // A token minted before users.token_epoch existed carries no epoch claim. It is read
            // as 0, which is the column default, so tokens in flight when the column was added
            // stay valid rather than logging every user out at once on deploy.
            int tokenEpoch = claims.tokenEpoch() == null ? 0 : claims.tokenEpoch();
            // The epoch is a field of the projection the status check already loads, not a second
            // query. Adding it to the projection interface widens that one SELECT list.
            return userRepository
                    .findProjectedByIdAndDeletedAtIsNull(claims.userId())
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                    .filter(user -> epochOf(user) == tokenEpoch)
                    .map(securityMapper::toUserPrincipal);
        } catch (AppException ex) {
            // Every JWT failure (expired, invalid signature, blacklisted lookup) was previously
            // indistinguishable in logs -- this is the only visibility into why auth failed.
            log.debug("JWT authentication rejected: {}", ex.getErrorCode());
            return Optional.empty();
        }
    }

    // Null only for a row written before the column existed, which the NOT NULL DEFAULT 0 on
    // users.token_epoch rules out; read defensively so a projection change cannot NPE the filter.
    private static int epochOf(UserSecurityProjection user) {
        return user.getTokenEpoch() == null ? 0 : user.getTokenEpoch();
    }
}
