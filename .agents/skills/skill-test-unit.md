---
trigger: model_decision
description: Load when writing unit tests for any service implementation or infrastructure class. Covers annotation stack, mock wiring, naming, and assertion patterns derived from actual codebase tests.
---

# Skill: Unit Test

## When to use

Writing tests for any class in `modules/{module}/service/impl/` or `common/security/impl/`.

## Input required

- Class under test
- All constructor-injected dependencies (to be mocked)

## Steps

1. Name the class `{SubjectClass}Test` in the mirror package under `src/test/`.
2. If the test needs `@Mock` fields, add `@ExtendWith(MockitoExtension.class)`.  
   Pure unit tests (direct construction, no mocked dependencies) need no annotation.
3. Declare one `@Mock` field per constructor dependency:
   ```java
   @Mock private UserRepository userRepository;
   @Mock private PasswordEncoder passwordEncoder;
   ```
4. Construct the subject in `@BeforeEach` by calling the constructor directly:
   ```java
   @BeforeEach
   void setUp() {
       service = new AuthServiceImpl(userRepository, credentialRepository, ...);
   }
   ```
   Do **not** use `@InjectMocks`.
5. Use `lenient()` only for stubs set up in `@BeforeEach` that are not consumed by every test:
   ```java
   lenient().when(authMapper.toUserSummaryResponse(any(), anyBoolean())).thenAnswer(...);
   ```
6. Write one `@Test` per logical scenario. Method name: `{method}_{condition}_{outcome}`:
   ```
   register_duplicateEmail_throwsConflict
   login_bannedUser_throwsAccountLocked
   logout_blacklistsAccessTokenAndRevokesRefreshToken
   ```
7. Stub with `when(...).thenReturn(...)`. Use `.thenAnswer(inv -> ...)` when the return value depends on input arguments (e.g., saving an entity and assigning an ID).
8. AssertJ only — no JUnit `assertEquals`:
   ```java
   assertThat(result.accessToken()).isEqualTo("ACCESS");
   assertThatThrownBy(() -> service.login(req, httpReq))
       .isInstanceOf(AppException.class)
       .extracting(ex -> ((AppException) ex).getErrorCode())
       .isEqualTo(ApiErrorCode.AUTH_INVALID_CREDENTIALS);
   assertThatCode(() -> service.logout(req)).doesNotThrowAnyException();
   ```
9. Verify side effects:
   ```java
   verify(userRepository).save(any(User.class));
   verify(mailService, never()).sendPasswordReset(anyString(), anyString(), anyString());
   verify(refreshTokenService, times(1)).revoke("RAW");
   ```
10. Use `ArgumentCaptor` to assert on arguments passed to a mock:
    ```java
    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(mailService).sendPasswordReset(eq(email), eq(displayName), urlCaptor.capture());
    assertThat(urlCaptor.getValue()).startsWith("http://localhost:5173/reset-password?token=");
    ```
11. For SecurityContext-dependent logic, set the context in the test and clear it in a `finally`:
    ```java
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("principal", rawAccessToken));
    try {
        service.logout(new RefreshRequest("REFRESH-RAW"));
    } finally {
        SecurityContextHolder.clearContext();
    }
    ```
12. Extract common test entities into private `static` factory methods:
    ```java
    private static User activeUser() { ... }
    private static UserCredential credential(UUID userId, String hash) { ... }
    ```

## Output contract

- `@ExtendWith(MockitoExtension.class)` when `@Mock` fields are present
- `@Mock` for each constructor dependency
- Impl constructed manually in `@BeforeEach`
- AssertJ assertions only
- One scenario per `@Test` method
- No Spring context loaded, no `@SpringBootTest`

## Checklist

- [ ] `@ExtendWith(MockitoExtension.class)` present if using `@Mock` annotations
- [ ] Every constructor dependency has a `@Mock` field
- [ ] `@BeforeEach` constructs impl via constructor — no `@InjectMocks`
- [ ] Method names follow `method_condition_outcome` pattern
- [ ] AssertJ used (not JUnit `assertEquals`)
- [ ] `assertThatThrownBy` used for exception cases
- [ ] `assertThatCode(...).doesNotThrowAnyException()` for no-throw cases
- [ ] No `@SpringBootTest` or context loading
