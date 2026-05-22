package com.app.modules.social.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import com.app.modules.social.converter.FollowStatusConverter;
import com.app.modules.social.enums.FollowStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "follows")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Follow {

    @EmbeddedId private FollowId id;

    @Convert(converter = FollowStatusConverter.class)
    @Column(name = "status", nullable = false)
    private FollowStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
