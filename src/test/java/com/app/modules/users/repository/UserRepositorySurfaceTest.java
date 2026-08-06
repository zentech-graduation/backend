package com.app.modules.users.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.app.modules.comment.repository.CommentUserRepository;
import com.app.modules.message.repository.MessageUserRepository;
import com.app.modules.post.repository.PostUserRepository;
import com.app.modules.social.repository.SocialUserRepository;
import com.app.modules.story.repository.StoryUserRepository;

/**
 * Structural guard: no repository interface over {@link com.app.modules.users.entity.User} may
 * expose an unfiltered {@code findById}, {@code findAll}, {@code findAllById}, or {@code
 * existsById}, since every one of those would silently return or count soft-deleted users. Each
 * interface must extend the bare {@code Repository<User, UUID>} marker and declare only the
 * filtered finders it actually uses, not {@code JpaRepository}, which would reintroduce the
 * unfiltered surface as a side effect of a future edit.
 */
class UserRepositorySurfaceTest {

    private static final Set<String> UNFILTERED_METHOD_NAMES =
            Set.of("findById", "findAll", "findAllById", "existsById");

    private static final List<Class<?>> USER_REPOSITORIES =
            List.of(
                    UserRepository.class,
                    CommentUserRepository.class,
                    MessageUserRepository.class,
                    PostUserRepository.class,
                    SocialUserRepository.class,
                    StoryUserRepository.class);

    @Test
    void noUserRepositoryExposesAnUnfilteredFinder() {
        for (Class<?> repository : USER_REPOSITORIES) {
            List<String> offendingMethods =
                    Stream.of(repository.getMethods())
                            .map(Method::getName)
                            .filter(UNFILTERED_METHOD_NAMES::contains)
                            .distinct()
                            .toList();

            assertThat(offendingMethods)
                    .as("%s must not expose an unfiltered finder", repository.getSimpleName())
                    .isEmpty();
        }
    }
}
