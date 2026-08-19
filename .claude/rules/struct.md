---
trigger: model_decision
description: Load when working on App (social network). Contains the authoritative project map.
---

# Project Structure — App (Social Network)

## 0. Application Purpose

Instagram-style social network: profiles, follow graph, photo/video/carousel posts, likes/saves, nested comments, 24-hour stories, 1-1 and group DMs, hashtags, ranked feed.

Private accounts enforce pending follow requests. Content moderation uses a report system with an admin audit log. A recommendation subsystem tracks behavioral events and interaction scores for feed ranking.

User roles: `user`, `moderator`, `admin`. Architecture: **Modular Monolith**.

---

## 1. Directory Structure

```text
app/
├── .agents/
│   ├── rules/                      # BASE.md, CHANGELOG_RULE.md, COMMENT_STYLE.md, DOC_FIRST.md, STRUCT.md
│   └── skills/                     # Skill definitions
├── .github/
│   ├── workflows/                  # pr-lint.yml (Conventional Commits), pr-size.yml (PR size labels)
│   ├── ISSUE_TEMPLATE/             # bug_report.yml, feature_request.yml, config.yml
│   ├── CODEOWNERS                  # Per-module review ownership
│   └── pull_request_template.md
├── database/
│   └── schema.sql                  # Reference PostgreSQL final-state schema (not applied by Flyway)
├── docker/                         # Scaffolded (.gitkeep)
├── docs/
│   └── modules/
│       ├── GLOBAL_RULES.md         # Cross-module data rules (enum/config table contracts)
│       └── {module}/DATA_RULES.md  # Per-module data access and write rules (13 modules)
├── src/
│   ├── main/
│   │   ├── java/com/app/
│   │   │   ├── common/             # Cross-cutting infrastructure (see §2)
│   │   │   ├── modules/            # 14 domain modules (see §2)
│   │   │   └── Application.java    # @SpringBootApplication @ConfigurationPropertiesScan
│   │   └── resources/
│   │       ├── db/migration/       # Flyway V01-V66 SQL migrations
│   │       ├── elasticsearch/
│   │       │   └── settings/       # hashtags.json, posts.json (Elasticsearch index settings)
│   │       ├── resilience/
│   │       │   ├── circuitbreaker/ # resilience4j-dev.yml, resilience4j-prod.yml
│   │       │   ├── ratelimiter/    # resilience4j-dev.yml, resilience4j-prod.yml
│   │       │   └── retry/          # resilience4j-dev.yml, resilience4j-prod.yml
│   │       ├── templates/mail/     # email-verification.html, oauth-account-no-password.html,
│   │       │                       # password-changed.html, password-reset.html, welcome.html
│   │       ├── application.yaml    # Core config (active profile: dev)
│   │       ├── application-dev.yml # Dev: JPA show-sql, Swagger at /api-docs, relaxed rate limits
│   │       ├── application-prod.yml# Prod: show-sql off, Swagger disabled
│   │       ├── banner.txt
│   │       └── logback-spring.xml  # Rolling file + console logging
│   └── test/
│       └── java/com/app/
│           ├── ApplicationTests.java                                    # Context smoke test
│           ├── common/config/{elasticsearch,rabbit}/                    # Config unit/integration tests
│           ├── common/exception/                                        # AppException, GlobalExceptionHandler tests
│           ├── common/inbox/service/impl/                               # ProcessedMessageServiceImplIT
│           ├── common/mail/{config,service/impl,util}/                  # Mail config/service/renderer tests
│           ├── common/outbox/{repository,service/impl}/                 # Outbox repo IT, publisher/service tests
│           ├── common/response/                                         # ApiResponse tests
│           ├── common/security/{filter,jwt,service/impl,util}/          # Security unit tests
│           └── modules/{admin,auth,comment,hashtag,media,message,notification,post,recommendation,report,social,story,users}/  # Module tests (see §2)
├── docker-compose.yaml             # Local dev: PostgreSQL, RabbitMQ, Redis, Elasticsearch
├── pom.xml
├── mvnw / mvnw.cmd
├── AGENTS.md                       # Agent instructions (root-level)
├── CHANGELOG.md
├── CONTRIBUTING.md
├── README.md
├── SECURITY.md
├── .env                            # Local environment variables (gitignored)
└── .env.example                    # Environment variable template
```

---

## 2. Source Code Architecture

### `common/` — Implemented Infrastructure

| Package | Key Classes |
|---------|------------|
| `common/` | `ApiConstants` |
| `common/base/` | `BaseController` |
| `common/config/app/` | `AppProperties` |
| `common/config/elasticsearch/` | `ElasticsearchConfig`, `ElasticsearchProperties` |
| `common/config/openapi/` | `OpenApiConfig` |
| `common/config/rabbit/` | `RabbitMqPublisherConfig`, `RabbitMqTopologyConfig` |
| `common/config/redis/` | `RedisConfig`, `RateLimitProperties` |
| `common/config/security/` | `SecurityProperties` |
| `common/config/websocket/` | `WebSocketBrokerConfig` |
| `common/enums/` | `ApiErrorCode`, `ApiSuccessCode` |
| `common/exception/` | `ApiException`, `AppException`, `GlobalExceptionHandler` |
| `common/inbox/entity/` | `ProcessedMessage` |
| `common/inbox/enums/` | `ProcessedMessageResult` |
| `common/inbox/repository/` | `ProcessedMessageRepository`, `ProcessedMessageRepositoryCustom`, `ProcessedMessageRepositoryImpl` |
| `common/inbox/service/` | `ProcessedMessageService` |
| `common/inbox/service/impl/` | `ProcessedMessageServiceImpl` |
| `common/messaging/` | `DeadLetterPublisher`, `DomainEventMessageParser` |
| `common/messaging/config/` | `ConsumerRetryProperties`, `MessagingConsumerConfig` |
| `common/messaging/exception/` | `PermanentMessageException` |
| `common/outbox/config/` | `OutboxPublisherConfig`, `OutboxPublisherProperties` |
| `common/outbox/entity/` | `OutboxEvent` |
| `common/outbox/enums/` | `OutboxEventStatus` |
| `common/outbox/exception/` | `OutboxPublishException` |
| `common/outbox/model/` | `DomainEventEnvelope`, `DomainEventEnvelopeJson` |
| `common/outbox/repository/` | `OutboxEventRepository`, `OutboxEventRepositoryCustom`, `OutboxEventRepositoryImpl` |
| `common/outbox/service/` | `OutboxService`, `OutboxPublisherService`, `OutboxPublisherStateService` |
| `common/outbox/service/impl/` | `OutboxServiceImpl`, `OutboxPublisherServiceImpl`, `OutboxPublisherStateServiceImpl` |
| `common/response/` | `ApiResponse<T>`, `CursorPageResponse<T>`, `PageResponse<T>` |
| `common/security/config/` | `CorsProperties`, `SecurityConfig` |
| `common/security/filter/` | `AuthRateLimitFilter`, `JwtAuthenticationFilter` |
| `common/security/jwt/` | `JwtClaims`, `JwtProperties`, `JwtTokenProvider` |
| `common/security/service/` | `RateLimiterService`, `RefreshTokenService`, `TokenBlacklistService` |
| `common/security/service/impl/` | `RateLimiterServiceImpl`, `RefreshTokenServiceImpl`, `TokenBlacklistServiceImpl` |
| `common/security/user/` | `SecurityMapper`, `UserPrincipal` |
| `common/security/util/` | `CachedBodyHttpServletRequest`, `IpExtractor`, `SecurityUtils` |
| `common/security/websocket/` | `JwtHandshakeInterceptor`, `WebSocketSessionRegistry`, `SessionTrackingWebSocketHandlerDecoratorFactory`, `WebSocketRevocationSweepService` |
| `common/settings/repository/` | `SystemSettingRepository` |
| `common/settings/service/` | `SystemSettingService` |
| `common/settings/service/impl/` | `SystemSettingServiceImpl` |

### Feature Module Layer Pattern

```text
{module}/
├── api/          # @RequestMapping interface contracts (optional)
├── config/       # Module-specific @ConfigurationProperties (optional)
├── consumer/     # RabbitMQ @RabbitListener consumers (optional)
├── controller/   # REST endpoints — delegates to Service only
├── converter/    # Spring Converter<S,T> implementations
├── dto/          # Request/Response objects (request/, response/ sub-packages)
├── entity/       # JPA @Entity classes
├── enums/        # Module-scoped enumerations
├── event/        # Domain event payload classes (optional)
├── exception/    # Module-specific exceptions (optional)
├── mapper/       # Entity ↔ DTO conversion (MapStruct interfaces)
├── messaging/    # RabbitMQ binding configs and event-type constants (optional)
├── repository/   # Spring Data JPA interfaces (+ custom impl as needed)
├── runner/       # ApplicationRunner beans for seeding/init (optional)
├── search/       # Elasticsearch @Document classes and search repositories (optional)
├── service/      # Interface + impl/ (@Transactional on impl methods only)
├── storage/      # Object storage abstractions (optional)
└── validation/   # Input validation logic and validated record wrappers (optional)
```

Extra sub-packages (e.g. `oauth2/`, `validation/`, `storage/`) follow the same pattern: add as needed per module.

### Feature Module Roster

| Module | Status | Sub-packages |
|--------|--------|--------------|
| `auth` | **Implemented** | api, config, controller, converter, dto/{request,response}, entity, enums, exception, mapper, messaging, oauth2, repository, service/impl, validation |
| `mail` | **Implemented** | config, config/resend, config/smtp, enums, service/impl, util |
| `users` | **Implemented** | api, controller, converter, dto/{request,response}, entity, enums, mapper, repository, service/impl |
| `social` | **Implemented** | api, controller, converter, dto/response, entity, enums, mapper, messaging, repository, service/impl |
| `media` | **Implemented** | api, config, controller, converter, dto/{request,response}, entity, enums, mapper, messaging, repository, service/impl, storage, validation |
| `post` | **Implemented** | api, config, consumer, controller, converter, dto/{request,response}, entity, enums, event, live, mapper, messaging, repository, runner, search, service/impl, validation |
| `hashtag` | **Implemented** | api, config, consumer, controller, converter, dto/{request,response}, entity, enums, event, mapper, messaging, repository, runner, search, service/impl |
| `notification` | **Implemented** | api, config, controller, dto/response, entity, entity/converter, entity/enums, live, mapper, messaging, repository, service/impl |
| `comment` | **Implemented** | api, config, consumer, controller, dto/{request,response}, entity, live, mapper, messaging, observability, repository, service/impl, util |
| `story` | **Implemented** | api, consumer, controller, converter, dto/{request,response}, entity, enums, mapper, messaging, repository, service/impl |
| `message` | **Implemented** | api, config, controller, converter, dto/{request,response}, entity, enums, mapper, repository, service/impl |
| `report` | **Implemented** | api, controller, converter, dto/{request,response}, entity, enums, mapper, repository, service/impl |
| `admin` | **Implemented** | api, config, controller, converter, dto/{request,response}, entity, enums, mapper, messaging, repository, service/impl |
| `recommendation` | Scaffolded | service/impl (`UserEventsPartitionJob` only — no controller, no repository, no service interface) |

**Module responsibilities:**
- **`auth`**: Login, register, OAuth2 (Google), JWT refresh, password reset, email verification, forgot-password timing equalization, OAuth2 code exchange.
- **`mail`**: Transactional email via Resend SDK; Thymeleaf templates; `MailTemplate` enum drives template selection; `MailSender` interface abstracts transport.
- **`users`**: Public and private user profiles, user settings, role/status management.
- **`social`**: Follow graph (public/private accounts with pending follow), block list, follow-event publishing via outbox.
- **`media`**: Pre-signed Cloudflare R2 upload URLs, media asset lifecycle, MIME/metadata/path validation.
- **`post`**: Post CRUD (image/video/carousel), likes, saves, post edit history, visibility enforcement, Elasticsearch index sync via outbox.
- **`hashtag`**: Hashtag creation/normalization, trending computation, Elasticsearch index sync via outbox, trigram-search fallback, and the `active`/`banned`/`deleted` lifecycle that governs what every hashtag surface shows and what every post write path accepts. `HashtagLifecycleService` owns the status transitions, the immediate `hashtag_trending` purge, and the status-spanning administrative reads.
- **`notification`**: Notification persistence and retrieval; `SocialNotificationConsumer` handles `user.followed.v1` and `user.follow-requested.v1` events.
- **`comment`**: Threaded comment CRUD (create with idempotency, edit, soft-delete subtree), likes, moderation, and real-time live fanout via WebSocket (STOMP over SockJS); `CommentNotificationConsumer` handles `comment.created.v1` and `comment.liked.v1` for notifications; `CommentLiveFanoutConsumer` fans out all `comment.*` events to connected WebSocket sessions; `CommentMaintenanceScheduler` performs periodic pruning tasks.
- **`report`**: User-submitted content flag lifecycle (submit, list, triage, status transitions); `ReportServiceImpl` enforces self-report prevention, duplicate suppression, entity existence validation, valid status-machine transitions, and resolution-note requirements for terminal states.
- **`admin`**: Immutable moderation audit log, atomic moderation actions, the warning and strike discipline ladder, report escalation, the report-anchored moderation view of a reported entity, and the administrative hashtag registry; `AdminServiceImpl` handles ban/unban, suspend/unsuspend, post/comment remove/restore, and report resolve/dismiss, and `AdminHashtagServiceImpl` handles hashtag create/ban/unban/delete — each writing an `admin_actions` row and mutating the target entity in the same transaction.

### Transactional Outbox / Inbox Pattern

All domain events flow through shared outbox/inbox infrastructure in `common/outbox/` and `common/inbox/`.

**Outbox (producer side)**:
- `OutboxService.enqueue()` (`PROPAGATION.MANDATORY`) persists a `DomainEventEnvelope` into `outbox_events` within the same transaction as the domain write.
- `OutboxPublisherServiceImpl` runs on a configurable `@Scheduled` fixed delay: claims a batch of `PENDING` rows using `FOR UPDATE SKIP LOCKED`, publishes to RabbitMQ with publisher confirms, then marks each row `PUBLISHED` or schedules retry / marks `DEAD` based on broker confirm outcome.
- `OutboxEventStatus` lifecycle: `PENDING` → `PROCESSING` → `PUBLISHED` | `DEAD`.
- Configuration namespace: `app.outbox.publisher.*` — batch size, max attempts, confirm timeout, processing lease, per-attempt retry backoffs.

**Inbox (consumer side)**:
- `ProcessedMessageService.processOnce(consumerName, eventId, handler)` guards all consumers against duplicate delivery using `INSERT … ON CONFLICT DO NOTHING RETURNING` into `processed_messages`.
- Returns `PROCESSED` on first execution; `DUPLICATE` (skips handler) on replay.

**Consumer retry policy**:
- On transient failure, consumers nack without requeue and schedule a dead-letter via `DeadLetterPublisher`.
- `PermanentMessageException` bypasses retry and routes directly to the DLQ.
- Configuration namespace: `app.messaging.consumer.*` — max attempts, per-attempt backoff durations.

### Test Coverage

Regenerated from `git ls-files` via `.workspace/scripts/regenerate_struct_md.sh`; 206 test classes total. The roster below has not been regenerated since well before that count and is missing entries across several modules; only the rows this branch changes are corrected here.

| Package | Test Classes |
|---------|-------------|
| `(root)` | `ApplicationTests` |
| `common` | `ApiConstantsSocialTest`, `ApiConstantsUnroutedFieldsTest`, `UpdatedAtSingleWriterIT` |
| `common/base` | `BaseControllerTest` |
| `common/config` | `ProdProfileConsumerActivationIT` |
| `common/config/elasticsearch` | `ElasticsearchConfigTest`, `ElasticsearchHealthIT` |
| `common/config/openapi` | `OpenApiContractIT` |
| `common/config/rabbit` | `RabbitMqTopologyConfigTest` |
| `common/config/security` | `RefreshCookiePropertiesTest`, `SecurityPropertiesValidationTest` |
| `common/enums` | `ApiErrorCodeMessageTest` |
| `common/exception` | `ApiExceptionTest`, `AppExceptionTest`, `GlobalExceptionHandlerTest`, `HttpNegotiationExceptionHandlersIT`, `MalformedRequestBodyIT` |
| `common/inbox/service/impl` | `ProcessedMessageServiceImplIT` |
| `common/mail/config` | `MailPropertiesBindingTest`, `MailTransportSelectionTest` |
| `common/mail/service/impl` | `MailServiceImplTest`, `ResendMailSenderTest`, `SmtpMailSenderTest`, `TemplateMailSenderParityTest` |
| `common/mail/util` | `MailTemplateRendererTest` |
| `common/messaging` | `DeadLetterPublisherTest` |
| `common/outbox/repository` | `OutboxEventRepositoryIT` |
| `common/outbox/service/impl` | `OutboxPublisherRabbitMqIT`, `OutboxPublisherServiceImplTest`, `OutboxServiceImplTest` |
| `common/pagination` | `CursorCodecTest`, `KeysetPageTest`, `OffsetCursorCodecTest`, `OffsetPageableTest`, `TimeCursorsTest` |
| `common/response` | `ApiResponseTest`, `CursorPageResponseTest`, `ViewerRelationshipResponseTest` |
| `common/web` | `StrictQueryParameterInterceptorTest` |
| `common/security/config` | `CorsPropertiesTest` |
| `common/security/filter` | `AuthRateLimitFilterTest`, `JwtAuthenticationFilterTest` |
| `common/security/jwt` | `JwtTokenProviderTest` |
| `common/security/service/impl` | `RateLimiterServiceImplTest`, `RefreshTokenServiceImplTest`, `TokenBlacklistServiceImplTest`, `TokenPrincipalResolverImplTest` |
| `common/security/user` | `UserPrincipalTest` |
| `common/security/util` | `CachedBodyHttpServletRequestTest`, `IpExtractorTest`, `SecurityUtilsTest` |
| `common/security/websocket` | `BrokerTopicSendGuardIT`, `JwtHandshakeInterceptorTest`, `WebSocketHandshakeRateLimitIT`, `WebSocketRevocationIT`, `WebSocketRevocationSweepServiceTest` |
| `common/settings/service/impl` | `SystemSettingServiceImplTest` |
| `modules/admin/controller` | `AdminControllerIT`, `AdminDisciplineControllerIT`, `AdminHashtagControllerIT` |
| `modules/admin/dto/request` | `AdminUpdateHashtagRequestDeserializationTest` |
| `modules/admin/repository` | `AdminActionKeysetRowLossIT`, `AdminActionRepositoryTest`, `UserWarningRepositoryIT` |
| `modules/admin/service/impl` | `AdminServiceImplTest`, `UserDisciplineServiceImplTest` |
| `modules/auth/controller` | `AuthControllerIT`, `PasswordPolicyIT` |
| `modules/auth/converter` | `OAuthProviderConverterTest` |
| `modules/auth/cookie` | `RefreshTokenCookieManagerTest` |
| `modules/auth/dto/request` | `RegisterRequestDeserializationTest`, `ResetPasswordRequestDeserializationTest` |
| `modules/auth/messaging` | `AuthMailEventConsumerRabbitMqIT`, `AuthMailEventConsumerTest`, `AuthMailEventHandlerTest` |
| `modules/auth/oauth2` | `CookieOAuth2AuthorizationRequestRepositoryTest`, `CustomOidcUserServiceTest`, `CustomOidcUserTest`, `OAuth2AuthenticationFailureHandlerTest` |
| `modules/auth/service/impl` | `AuthForgotPasswordEventServiceImplTest`, `AuthMailEventServiceImplTest`, `AuthResendVerificationEventServiceImplTest`, `AuthServiceImplTest`, `ForgotPasswordTimingEqualizerTest`, `OAuth2ExchangeCodeServiceImplTest`, `RefreshTokenPurgeJobTest`, `TokenServiceImplTest` |
| `modules/auth/validation` | `PasswordPolicyValidatorTest`, `UserStateValidatorTest` |
| `modules/comment/config` | `CommentWebSocketConfigTest` |
| `modules/comment/consumer` | `CommentNotificationConsumerIT` |
| `modules/comment/controller` | `CommentControllerIT` |
| `modules/comment/live` | `CommentLiveBlockFilterIT`, `CommentStompSendAuthIT`, `CommentWebSocketAccountStatusIT`, `CommentWebSocketHandshakeRejectionIT`, `CommentWebSocketLiveDeliveryIT` |
| `modules/comment/repository` | `CommentKeysetRowLossIT`, `CommentTopLikedQueryIT` |
| `modules/comment/service/impl` | `CommentAuthorEmbeddingIT`, `CommentModerationServiceImplTest`, `CommentPinnedTopCommentsIT`, `CommentServiceImplTest`, `CommentViewerStateIT`, `CommentViewerStateServiceImplTest` |
| `modules/hashtag/consumer` | `HashtagIndexSyncConsumerIT`, `HashtagIndexSyncConsumerTest` |
| `modules/hashtag/controller` | `HashtagControllerIT` |
| `modules/hashtag/repository` | `HashtagRepositoryIT` |
| `modules/hashtag/service/impl` | `HashtagLifecycleServiceImplTest`, `HashtagSearchServiceImplTest`, `HashtagServiceImplTest`, `HashtagTrendingServiceImplTest`, `HashtagTrendingSnapshotIT` |
| `modules/media/controller` | `MediaControllerIT` |
| `modules/media/repository` | `MediaAssetRepositoryIT` |
| `modules/media/service/impl` | `MediaAssetRegistrarTest`, `MediaEventServiceImplTest`, `MediaServiceImplTest` |
| `modules/media/storage` | `MediaStorageKeyGeneratorTest`, `R2ObjectStoragePresignServiceTest` |
| `modules/media/validation` | `MediaMetadataValidatorTest` |
| `modules/message/config` | `MessagePropertiesTest` |
| `modules/message/controller` | `MessageControllerIT` |
| `modules/message/converter` | `MessageTypeConverterTest` |
| `modules/message/repository` | `ConversationKeysetRowLossIT` |
| `modules/message/service/impl` | `ConversationServiceImplTest` |
| `modules/notification/config` | `NotificationWebSocketConfigTest` |
| `modules/notification/controller` | `NotificationControllerIT` |
| `modules/notification/entity/converter` | `NotificationTypeConverterTest` |
| `modules/notification/live` | `NotificationLiveDeliveryIT`, `NotificationLiveFanoutConsumerTest`, `NotificationOnlyWebSocketConfigIT`, `NotificationPushLatencyIT`, `NotificationWebSocketSubscriptionAuthIT` |
| `modules/notification/messaging` | `SocialNotificationConsumerIT`, `SocialNotificationConsumerTest` |
| `modules/notification/repository` | `NotificationKeysetRowLossIT` |
| `modules/notification/service/impl` | `NotificationAuthorEmbeddingIT`, `NotificationServiceImplTest` |
| `modules/post/consumer` | `PostIndexSyncConsumerIT`, `PostIndexSyncConsumerTest` |
| `modules/post/live` | `PostLikeLiveDeliveryIT`, `PostOnlyWebSocketConfigIT` |
| `modules/post/controller` | `PostBannedHashtagIT`, `PostControllerIT` |
| `modules/post/repository` | `PostKeysetRowLossIT` |
| `modules/post/service/impl` | `PostAuthorEmbeddingIT`, `PostLikeServiceImplTest`, `PostResponseAssemblerTest`, `PostSaveServiceImplTest`, `PostSearchServiceImplTest`, `PostServiceImplTest`, `PostViewerStateIT`, `PostViewerStateServiceImplTest`, `PostVisibilityServiceImplTest` |
| `modules/recommendation/service/impl` | `UserEventsPartitionJobTest` |
| `modules/report/controller` | `ReportControllerIT` |
| `modules/report/repository` | `ReportKeysetRowLossIT`, `ReportRepositoryIT` |
| `modules/report/service/impl` | `ReportServiceImplTest` |
| `modules/social/controller` | `SocialControllerIT` |
| `modules/social/converter` | `FollowStatusConverterTest` |
| `modules/social/repository` | `FollowKeysetRowLossIT`, `FollowRepositoryIT` |
| `modules/social/service/impl` | `BlockedListIT`, `SocialEventServiceImplTest`, `SocialRelationshipIT`, `SocialServiceImplTest` |
| `modules/story/consumer` | `StoryNotificationConsumerIT`, `StoryNotificationConsumerTest` |
| `modules/story/controller` | `StoryControllerIT` |
| `modules/story/repository` | `StoryViewKeysetRowLossIT` |
| `modules/story/service/impl` | `StoryServiceImplTest`, `StoryViewServiceImplTest`, `StoryVisibilityServiceImplTest` |
| `modules/users/controller` | `UserControllerIT` |
| `modules/users/mapper` | `UserMapperTest` |
| `modules/users/repository` | `UserRepositorySurfaceTest` |
| `modules/users/service/impl` | `UserProfileViewerStateIT`, `UserSearchIT`, `UserSearchServiceImplTest`, `UserServiceImplTest`, `UserSummaryServiceIT`, `UserSummaryServiceImplTest`, `UsernameLookupIT` |

---

## 3. Infrastructure

### Database

- Engine: **PostgreSQL** (docker-compose: `postgres:latest`)
- Migration: **Flyway** (`out-of-order: false`); 68 migrations at `src/main/resources/db/migration/`. V57, V63, V66 and V68 build their indexes `CONCURRENTLY` and carry a `.sql.conf` sidecar setting `executeInTransaction=false`:

| Migration | Description |
|-----------|-------------|
| V01 | create_extensions_and_enums |
| V02 | create_users_auth_tables |
| V03 | create_user_settings_and_push |
| V04 | create_social_graph |
| V05 | create_media_assets |
| V06 | create_post_tables |
| V07 | create_comment_tables |
| V08 | create_hashtag_tables |
| V09 | create_story_tables |
| V10 | create_notification_table |
| V11 | create_message_tables |
| V12 | create_report_table |
| V13 | create_admin_table |
| V14 | create_recommendation_tables |
| V15 | create_indexes |
| V16 | create_triggers_and_functions |
| V17 | create_views |
| V18 | add_metadata_config_tables |
| V19 | create_outbox_events |
| V20 | create_processed_messages |
| V21 | add_outbox_claim_lease_columns |
| V22 | create_post_edit_history |
| V23 | drop_legacy_plaintext_credential_columns |
| V24 | add_refresh_tokens_expires_index |
| V25 | add_text_post_type |
| V26 | add_comment_moderation_status |
| V27 | create_comment_write_idempotency |
| V28 | add_user_events_upcoming_partitions |
| V29 | preserve_admin_action_audit_history |
| V30 | add_reports_duplicate_unique_index |
| V31 | create_message_write_idempotency |
| V32 | preserve_message_sender_history |
| V33 | add_direct_conversation_pair_key |
| V34 | add_keyset_tiebreaker_indexes |
| V35 | add_like_save_keyset_indexes |
| V36 | add_comment_keyset_indexes |
| V37 | add_follow_keyset_indexes |
| V38 | add_story_view_keyset_index |
| V39 | add_notification_keyset_index |
| V40 | add_blocks_keyset_index |
| V41 | add_comment_top_liked_index |
| V42 | add_username_case_insensitive_index |
| V43 | align_username_index_with_soft_delete_policy |
| V44 | add_email_case_insensitive_index |
| V45 | add_comment_edited_at |
| V46 | add_post_likes_user_keyset_index |

| V47 | add_notification_post_id |

| V48 | add_users_banner_url |

| V49 | add_story_likes |

| V50 | remove_group_conversations |

| V51 | order_follow_counter_locks |

| V52 | add_conversation_participant_customization |

| V53 | add_conversation_manual_unread_flag |

| V54 | add_admin_action_type_values |

| V55 | add_moderation_action_configs_rows |

| V56 | add_users_admin_visibility_columns |

| V57 | add_users_admin_visibility_indexes |

| V58 | add_users_token_epoch |

| V59 | add_posts_status_before_moderation |

| V60 | add_notification_type_warning |

| V61 | add_notification_type_configs_warning_row |

| V62 | create_user_discipline_tables |

| V63 | create_user_discipline_indexes |

| V64 | add_report_status_escalated |

| V65 | add_reports_escalation_columns |

| V66 | add_reports_escalated_index |
| V67 | add_hashtag_status |
| V68 | add_hashtag_status_indexes |

- Reference schema: `database/schema.sql` (authoritative final-state; not applied by Flyway)
- Extensions: `pgcrypto` (UUID gen), `pg_trgm` (fuzzy username search), `btree_gin` (composite GIN indexes)

PostgreSQL enum types:

| Enum | Values |
|------|--------|
| `user_role` | `user`, `moderator`, `admin` |
| `user_status` | `active`, `suspended`, `deactivated`, `banned` |
| `post_status` | `draft`, `published`, `archived`, `removed` |
| `post_type` | `image`, `video`, `carousel`, `text` |
| `media_type` | `image`, `video` |
| `follow_status` | `pending`, `accepted` |
| `hashtag_status` | `active`, `banned`, `deleted` (V67) |
| `story_type` | `image`, `video` |
| `message_type` | `text`, `image`, `video`, `post_share`, `story_share` |
| `report_type` | `post`, `comment`, `user`, `story`, `message` |
| `report_status` | `pending`, `reviewing`, `resolved`, `dismissed`, `escalated` (V64) |
| `report_reason` | `spam`, `nudity`, `violence`, `hate_speech`, `harassment`, `false_information`, `scam`, `other` |
| `notification_type` | `like_post`, `like_comment`, `comment_post`, `reply_comment`, `follow`, `follow_request`, `mention_post`, `mention_comment`, `story_view`, `message`, `warning` (V60) |
| `oauth_provider` | `google`, `facebook`, `apple` |
| `admin_action_type` | `ban_user`, `unban_user`, `suspend_user`, `unsuspend_user`, `remove_post`, `restore_post`, `remove_comment`, `restore_comment`, `resolve_report`, `dismiss_report`, `change_user_role`, `force_logout`, `warn_user`, `revoke_warning`, `issue_strike`, `revoke_strike`, `escalate_report`, `create_hashtag`, `edit_hashtag`, `ban_hashtag`, `unban_hashtag`, `delete_hashtag` (V54; the five hashtag values still have no caller, the rest do) |
| `event_type` | `post_view`, `post_like`, `post_unlike`, `post_save`, `post_unsave`, `post_share`, `post_comment`, `story_view`, `story_reply`, `profile_view`, `profile_follow`, `profile_unfollow`, `search`, `hashtag_click`, `comment_like`, `comment_reply`, `message_send`, `session_start`, `session_end`, `app_open` |

### Cache — Redis

- docker-compose: `redis:7-alpine`
- Implemented: `RedisConfig`, `RateLimitProperties`, `TokenBlacklistServiceImpl`, `RefreshTokenServiceImpl`, `RateLimiterServiceImpl`, `OAuth2ExchangeCodeServiceImpl` (Lua scripts for atomic ops)
- Key patterns in use:

| Key pattern | TTL | Owner |
|-------------|-----|-------|
| `auth:blacklist:{jti}` | remaining access token lifetime | `TokenBlacklistServiceImpl` |
| `auth:ratelimit:{endpoint}:{ip_or_email}` | sliding window | `RateLimiterServiceImpl` |
| `auth:token:email-verification:{sha256}` | 24h | `TokenServiceImpl` |
| `auth:token:email-verification:user:{userId}` | 24h (reverse index) | `TokenServiceImpl` |
| `auth:token:password-reset:{sha256}` | 15m | `TokenServiceImpl` |
| `auth:token:password-reset:user:{userId}` | 15m (reverse index) | `TokenServiceImpl` |
| `auth:oauth2:exchange:{code}` | 120s | `OAuth2ExchangeCodeServiceImpl` |

### Message Broker — RabbitMQ

- docker-compose: `rabbitmq:latest` (port 5672)
- Topology declared in `RabbitMqTopologyConfig`; publisher customized in `RabbitMqPublisherConfig`

**Exchanges:**

| Exchange | Type | Durable | Role |
|----------|------|---------|------|
| `social.events` | Topic | yes | Primary event bus for all domain events |
| `social.events.dlx` | Topic | yes | Dead-letter exchange for failed messages |
| `comment.live.events` | Fanout | yes | Live comment fanout tier; receives all `comment.*` events via exchange-to-exchange binding from `social.events` |
| `notification.live.events` | Fanout | yes | Live notification fanout tier; receives all `notification.*` events via exchange-to-exchange binding from `social.events` |
| `post.live.events` | Fanout | yes | Live post fanout tier; receives all `post.live.*` events via exchange-to-exchange binding from `social.events` |

**Queues and DLQs (all durable):**

| Queue | Dead-letter queue | DLQ routing key to `social.events.dlx` |
|-------|------------------|----------------------------------------|
| `mail.queue` | `mail.dlq` | `mail.dead-letter` |
| `notification.queue` | `notification.dlq` | `notification.dead-letter` |
| `hashtag.index.sync` | `hashtag.index.sync.dlq` | `hashtag.index.dead-letter` |
| `post.index.sync` | `post.index.sync.dlq` | `post.index.dead-letter` |
| `comment.notification.queue` | `comment.notification.dlq` | `comment.notification.dead-letter` |
| `story.notification.queue` | `story.notification.dlq` | `story.notification.dead-letter` |

| `admin.notification.queue` | `admin.notification.dlq` | `admin.notification.dead-letter` |

**Bindings (queue → `social.events`):**

| Queue | Routing key / pattern | Source config |
|-------|-----------------------|---------------|
| `mail.queue` | `user.registered.v1` | `AuthMailRabbitBindingConfig` |
| `mail.queue` | `auth.email-verification.requested.v1` | `AuthMailRabbitBindingConfig` |
| `mail.queue` | `auth.password-reset.requested.v1` | `AuthMailRabbitBindingConfig` |
| `mail.queue` | `auth.password-changed.v1` | `AuthMailRabbitBindingConfig` |
| `mail.queue` | `auth.oauth-account-no-password.v1` | `AuthMailRabbitBindingConfig` |
| `notification.queue` | `user.followed.v1` | `NotificationRabbitBindingConfig` |
| `notification.queue` | `user.follow-requested.v1` | `NotificationRabbitBindingConfig` |
| `hashtag.index.sync` | `hashtag.index.#` (wildcard) | `HashtagRabbitBindingConfig` |
| `post.index.sync` | `post.index.#` (wildcard) | `PostRabbitBindingConfig` |
| `comment.notification.queue` | `comment.created.v1` | `CommentRabbitBindingConfig` |
| `comment.notification.queue` | `comment.liked.v1` | `CommentRabbitBindingConfig` |
| `story.notification.queue` | `story.viewed.v1` | `StoryRabbitBindingConfig` |

| `admin.notification.queue` | `user.warned.v1` | `AdminRabbitBindingConfig` |
| `comment.live.events` (exchange) | `comment.#` (wildcard, exchange-to-exchange) | `RabbitMqTopologyConfig` |
| `notification.live.events` (exchange) | `notification.#` (wildcard, exchange-to-exchange) | `RabbitMqTopologyConfig` |
| `post.live.events` (exchange) | `post.live.#` (wildcard, exchange-to-exchange) | `RabbitMqTopologyConfig` |

**RabbitMQ configuration (application.yaml):**
- `publisher-confirm-type: correlated` — broker confirms wired to outbox acknowledge logic
- `publisher-returns: true` — unroutable messages returned to sender
- `template.mandatory: true` — mandatory flag on every send
- `listener.simple/direct.acknowledge-mode: manual` — consumers ack/nack explicitly

### Search — Elasticsearch

- docker-compose: `docker.elastic.co/elasticsearch/elasticsearch:9.0.3` (port 9200, `discovery.type: single-node`, security disabled)
- Client wired in `ElasticsearchConfig` using `app.elasticsearch.*` properties (URIs, optional Basic Auth, connection/socket timeouts)
- Index settings files: `src/main/resources/elasticsearch/settings/posts.json`, `hashtags.json`

**Indexed documents:**

| Document class | Index | `createIndex` | Setting path |
|----------------|-------|---------------|--------------|
| `PostDocument` | `posts` | `false` | `/elasticsearch/settings/posts.json` |
| `HashtagDocument` | `hashtags` | `false` | `/elasticsearch/settings/hashtags.json` |

`PostDocument` fields: `id`, `user_id` (Keyword), `caption` (Text + `caption.ngram`), `status` (Keyword), `hashtag_ids` (Keyword list), `created_at` (Date).

`HashtagDocument` fields: `id`, `name` (Keyword + `name.ngram`), `post_count` (Integer), `created_at` (Date).

Indexes are created at startup by `PostIndexSeedRunner` / `HashtagIndexSeedRunner` when Elasticsearch is reachable. Documents are synchronized asynchronously via outbox events consumed by `PostIndexSyncConsumer` / `HashtagIndexSyncConsumer`. The `elasticsearchSearch` circuit breaker gates search queries; fallback for hashtag search is PostgreSQL trigram.

### Resilience

Pre-configured Resilience4j (dev and prod profiles):

| Component | Instance | Dev config | Prod config |
|-----------|----------|------------|-------------|
| Circuit breaker | `default` | 10-call sliding window, 5 min calls, 50% failure threshold, 10s open wait, 3 half-open calls | 20-call sliding window, 10 min calls, 50% threshold, 30s open wait, 5 half-open calls |
| Circuit breaker | `elasticsearchSearch` | COUNT_BASED 10-call, 50% threshold, 10s open wait, 3 half-open, auto-transition | COUNT_BASED 20-call, 50% threshold, 30s open wait, 5 half-open, auto-transition, 5s slow-call threshold, 80% slow-call rate |
| Rate limiter | `lowTraffic` | 60 req / 30s, 5s timeout | 30 req / 30s, 5s timeout |
| Rate limiter | `mediumTraffic` | 120 req / 30s, 5s timeout | 60 req / 30s, 5s timeout |
| Rate limiter | `highTraffic` | 120 req / 30s, 5s timeout | 90 req / 30s, 5s timeout |
| Retry | `default` | 3 attempts, 1s initial, ×2 backoff | 3 attempts, 2s initial, ×2 backoff |

---

## 4. Technology Stack

| Component | Value |
|-----------|-------|
| Language | Java 21 (virtual threads: `spring.threads.virtual.enabled: true`) |
| Framework | Spring Boot 4.0.6 |
| Database | PostgreSQL |
| Cache | Redis |
| Message broker | RabbitMQ |
| Search | Elasticsearch 9.0.3 (`spring-boot-starter-data-elasticsearch`) |
| Object storage | Cloudflare R2 via AWS SDK v2 (`software.amazon.awssdk:s3 2.25.60`) |
| Transactional email | Resend SDK (`resend-java 3.1.0`) |
| Build | Maven (`./mvnw`) |
| Migrations | Flyway (`spring-boot-starter-flyway`, `flyway-database-postgresql`) |
| Resilience | Resilience4j (Spring Cloud 2025.1.1) |
| Security | Spring Security 6 |
| ORM | Spring Data JPA / Hibernate |
| Code generation | Lombok, MapStruct 1.6.3 |
| Observability | Micrometer + Prometheus, datasource-micrometer 2.2.1, Spring Actuator |
| Formatting | Spotless 2.46.1 (Google AOSP); run `./mvnw spotless:apply` |
| Testing | JUnit 5, Testcontainers 1.21.4 (postgresql, elasticsearch), Spring Boot test starters |
| CI/CD | GitHub Actions (`.github/workflows/pr-lint.yml`, `pr-size.yml`) |

---

## 5. Security

Implemented in `common/security/` and `modules/auth/`:

- **JWT**: `JwtTokenProvider` issues HS256-signed access tokens. Claims include `jti` (UUID per token, used for blacklisting). TTLs from `ACCESS_TOKEN_TTL` / `REFRESH_TOKEN_TTL`. Signing key from `JWT_SECRET`; issuer from `JWT_ISSUER`; audience from `JWT_AUDIENCE`.
- **Filter chain**: `JwtAuthenticationFilter` → `AuthRateLimitFilter`. `CachedBodyHttpServletRequest` enables body re-read in filters with a configurable byte-size limit.
- **Account state enforcement**: `UserStateValidator` throws typed `AppException` for banned, suspended, deactivated, and unverified-email states. `JwtAuthenticationFilter` also enforces status on every authenticated request.
- **Token blacklist**: Redis-backed (`TokenBlacklistServiceImpl`); on logout, `jti` stored until access token expiry (`auth:blacklist:{jti}`).
- **Refresh tokens**: SHA-256 hashed (`token_hash`) in PostgreSQL with device metadata and IP; revocable via `revoked_at`. `rotate()` uses `REQUIRES_NEW` propagation and a conditional UPDATE for concurrent-rotation detection and token-theft detection.
- **One-time tokens**: Email verification and password reset stored as SHA-256 hashes in Redis with TTL; atomic Lua-script consumption; issuing a new token invalidates the prior via reverse index.
- **OAuth2**: `CustomOidcUserService`, `OAuth2AuthenticationSuccessHandler`, `OAuth2AuthenticationFailureHandler` in `auth/oauth2/`. State parameter persisted as a tamper-evident HMAC-signed cookie via `CookieOAuth2AuthorizationRequestRepository` (key from `APP_COOKIE_SIGNING_SECRET`).
- **OAuth2 code exchange**: `OAuth2ExchangeCodeServiceImpl` stores a short-lived user-ID mapping (`auth:oauth2:exchange:{code}`, 120s TTL) consumed once by the frontend to complete the PKCE-like handshake.
- **Rate limiting**: `AuthRateLimitFilter` uses `RateLimiterServiceImpl` (Redis Lua sliding window); per-endpoint rules in `app.rate-limit.endpoint-rules`. Login bucket keyed by IP + email.
- **Trusted proxy**: `IpExtractor` reads `X-Forwarded-For` only when the direct peer IP matches a configured trusted-proxy CIDR list (`app.security.trusted-proxy-cidrs`).
- **Forgot-password timing**: `ForgotPasswordTimingEqualizer` normalises response time to a configurable floor (`app.auth.forgot-password.min-response-time` + jitter) to prevent user-enumeration via timing.

---

## 6. Key Conventions

- **Naming**: `{Entity}Controller`, `{Entity}Service` / `{Entity}ServiceImpl`, `{Entity}Repository`, `{Entity}Request` / `{Entity}Response`
- **Formatting**: Spotless / Google Java Format AOSP; import order: `java`, `jakarta`, `org`, `com`; 4-space tab indent
- **Transactions**: `@Transactional` on Service impl methods only — never on interfaces or Controllers
- **Responses**: wrap all responses in `ApiResponse<T>`; use `PageResponse<T>` for offset pagination, `CursorPageResponse<T>` for cursor pagination
- **Async**: virtual threads enabled globally
- **Logging**: `timestamp | level | thread | traceId | logger | message`; rolling file 50 MB, 10 files, 500 MB cap
- **Comments**: English only; see `.agents/rules/COMMENT_STYLE.md`

---

## 7. Domain-Specific Invariants

- **Denormalized counters** (maintained by PostgreSQL triggers — never write from application code):
  - `users`: `follower_count`, `following_count`, `post_count`
  - `posts`: `like_count`, `comment_count`, `save_count`, `view_count`
  - `comments`: `like_count`, `reply_count`
  - `hashtags`: `post_count`
  - `stories`: `view_count`
- **`user_events` partitioning**: partitioned by month (`PARTITION BY RANGE (created_at)`). Pre-create monthly partitions before inserting events.
- **Comment depth cap**: `CHECK (depth BETWEEN 0 AND 10)`; adjacency list uses `root_id` for subtree queries.
- **Follow visibility**: insert to `follows` with `is_private = true` target → `status = 'pending'`; counters increment only on `status = 'accepted'` (trigger-enforced).
- **Story expiry**: `expires_at DEFAULT NOW() + INTERVAL '24 hours'`; `idx_stories_expires` implies a cleanup job.
- **`post_interaction_scores`**: written by background scheduler only — not from Controller or Service layers.
- **`user_similarity`**: `user_id_a < user_id_b` constraint eliminates duplicate pairs; populated by batch/ML jobs.
- **Config tables**: `notification_type_configs`, `moderation_action_configs`, `report_reason_configs` (V18) store display metadata for enum values. See `docs/modules/GLOBAL_RULES.md` for the enum/config table contract.
- **Swagger path**: configured at `/api-docs` in `application-dev.yml`; disabled in prod.
