package com.app.modules.social.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.modules.social.entity.Follow;
import com.app.modules.social.entity.FollowId;

@Repository
public interface FollowRepository extends JpaRepository<Follow, FollowId>, FollowRepositoryCustom {}
