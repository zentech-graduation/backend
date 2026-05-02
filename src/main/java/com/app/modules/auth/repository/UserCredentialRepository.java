package com.app.modules.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.modules.auth.entity.UserCredential;

@Repository
public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {

    Optional<UserCredential> findByUserId(UUID userId);
}
