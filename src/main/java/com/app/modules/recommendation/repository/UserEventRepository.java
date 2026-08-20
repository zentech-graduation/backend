package com.app.modules.recommendation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.modules.recommendation.entity.UserEvent;
import com.app.modules.recommendation.entity.UserEventId;

@Repository
public interface UserEventRepository
        extends JpaRepository<UserEvent, UserEventId>, UserEventRepositoryCustom {}
