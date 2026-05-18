---
trigger: model_decision
description: Load when writing integration tests for any controller.
---

# Skill: Integration Test (Controller)

## When to use
Writing end-to-end tests for any class in `modules/{module}/controller/`.

## Input required
- Controller under test
- Which services should be stubbed (e.g., `MailService` — always stub to avoid real email sends)
- Required `DynamicPropertySource` env vars

## Steps

1. Name the test class `{Module}ControllerIT`, placed in `src/test/java/com/app/modules/{module}/controller/`.
2. Annotate the class:
   ```java
   @SpringBootTest(
       webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
       properties = {
           "spring.profiles.active=dev",
           "spring.docker.compose.enabled=false",
           "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
       })
   @Testcontainers
   @AutoConfigureTestRestTemplate
   class {Module}ControllerIT { }
   ```
3. Declare Testcontainers:
   ```java
   @Container @ServiceConnection
   static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

   @Container
   static GenericContainer<?> redis =
       new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
   ```
4. Supply env vars via `@DynamicPropertySource`:
   ```java
   @DynamicPropertySource
   static void register(DynamicPropertyRegistry r) {
       r.add("spring.data.redis.host", redis::getHost);
       r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
       r.add("JWT_SECRET", () -> "integration-test-secret-32-chars-minimum-len!!");
       r.add("JWT_ISSUER", () -> "https://it.test.local");
       // ... all required env vars
   }
   ```
5. Inject `TestRestTemplate` and repositories under test via `@Autowired`.
6. Stub out services that have external side effects using `@MockitoBean`:
   ```java
   @MockitoBean private MailService mailService;
   ```
7. Write one `@Test` per scenario. Name: `{method}_{condition}_{expectedOutcome}`.
8. Make HTTP calls via `TestRestTemplate`:
   ```java
   ResponseEntity<Map> response = postJson("/api/v1/auth/register", body);
   assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
   assertThat(response.getBody().get("code")).isEqualTo("CREATED");
   ```
9. For database state assertions, inject repositories and query directly:
   ```java
   RefreshToken row = refreshTokenRepository.findByTokenHash(hash).orElseThrow();
   assertThat(row.getRevokedAt()).isNotNull();
   ```
10. Use unique per-test identifiers (e.g., `uniqueEmail("test_name")`) to avoid cross-test data conflicts without wiping the database between tests.
11. No `@Transactional` on `@SpringBootTest` integration tests — transactions committed by the app under test must be visible.

## Output contract
- Class in `src/test/java/com/app/modules/{module}/controller/{Module}ControllerIT.java`
- `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- `@Testcontainers @AutoConfigureTestRestTemplate`
- PostgreSQL and Redis containers declared
- `@DynamicPropertySource` supplies all required env vars
- External-side-effect services stubbed with `@MockitoBean`
- Database state verified via repository injection

## Checklist
- [ ] `@SpringBootTest(webEnvironment = RANDOM_PORT)` with correct `properties`
- [ ] `@Testcontainers @AutoConfigureTestRestTemplate` present
- [ ] PostgreSQL container: `postgres:16-alpine` with `@ServiceConnection`
- [ ] Redis container with `@DynamicPropertySource` for host/port
- [ ] All required env vars in `@DynamicPropertySource`
- [ ] `MailService` (and any other external-side-effect service) stubbed with `@MockitoBean`
- [ ] No `@Transactional` on the test class
- [ ] Database state verified via repository after each key assertion
