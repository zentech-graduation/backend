---
trigger: model_decision
description: Load when writing unit tests for any service implementation.
---

# Skill: Unit Test (Service)

## When to use
Writing tests for any class in `modules/{module}/service/impl/`.

## Input required
- Service impl class under test
- All constructor-injected dependencies (to be mocked)

## Steps

1. Name the test class `{ServiceImpl}Test`, placed in the mirror package under `src/test/`.
2. Annotate the class:
   ```java
   @ExtendWith(MockitoExtension.class)
   class {ServiceImpl}Test { }
   ```
3. Declare a `@Mock` field for every constructor dependency:
   ```java
   @Mock private UserRepository userRepository;
   @Mock private PasswordEncoder passwordEncoder;
   // ...
   ```
4. Instantiate the impl in `@BeforeEach` by calling the constructor directly with the mocked fields:
   ```java
   @BeforeEach
   void setUp() {
       service = new AuthServiceImpl(userRepository, credentialRepository, ...);
   }
   ```
5. Write one `@Test` method per logical scenario (happy path and each failure branch separately).
6. Test method names: `{methodName}_{condition}_{expectedOutcome}`, e.g., `login_invalidPassword_throwsAppException`.
7. Use AssertJ for assertions:
   ```java
   assertThat(result).isNotNull();
   assertThat(result.accessToken()).isNotBlank();
   assertThatThrownBy(() -> service.login(req, httpReq))
       .isInstanceOf(AppException.class)
       .extracting(e -> ((AppException) e).getErrorCode())
       .isEqualTo(ApiErrorCode.AUTH_INVALID_CREDENTIALS);
   ```
8. Stub with `when(...).thenReturn(...)`. Use `lenient()` only when stubbing in `@BeforeEach` for stubs not used in every test.
9. Verify side-effects: `verify(mock).method(args)` or `verify(mock, never()).method(any())`.
10. Use `ArgumentCaptor` when the test needs to assert on arguments passed to a mock:
    ```java
    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    assertThat(captor.getValue().getEmail()).isEqualTo(email);
    ```
11. Do **not** mock the system under test — only its dependencies.
12. No `@Transactional` on test classes.
13. No Javadoc on test methods.

## Output contract
- Test class in `src/test/java/com/app/modules/{module}/service/impl/{ServiceImpl}Test.java`
- `@ExtendWith(MockitoExtension.class)`
- `@Mock` for each dependency
- Impl constructed manually in `@BeforeEach`
- AssertJ assertions
- One scenario per test method
- No Spring context loaded

## Checklist
- [ ] `@ExtendWith(MockitoExtension.class)` present
- [ ] Every constructor dependency has a `@Mock` field
- [ ] `@BeforeEach` constructs impl via constructor
- [ ] Test method names follow `method_condition_outcome` pattern
- [ ] AssertJ used (not JUnit `assertEquals`)
- [ ] `assertThatThrownBy` used for exception cases
- [ ] No `@SpringBootTest` or context loading
