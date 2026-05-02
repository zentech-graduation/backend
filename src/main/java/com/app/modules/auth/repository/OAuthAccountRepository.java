package com.app.modules.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.modules.auth.entity.OAuthAccount;
import com.app.modules.auth.enums.OAuthProvider;

@Repository
public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, UUID> {

    Optional<OAuthAccount> findByProviderAndProviderId(OAuthProvider provider, String providerId);

    Optional<OAuthAccount> findByUserIdAndProvider(UUID userId, OAuthProvider provider);

    boolean existsByProviderAndProviderId(OAuthProvider provider, String providerId);
}
