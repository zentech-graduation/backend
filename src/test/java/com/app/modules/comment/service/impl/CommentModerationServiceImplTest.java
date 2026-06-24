package com.app.modules.comment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.app.modules.comment.config.CommentProperties;
import com.app.modules.comment.service.CommentModerationService.ModerationResult;

class CommentModerationServiceImplTest {

    private CommentModerationServiceImpl service(List<String> blockedWords) {
        return new CommentModerationServiceImpl(new CommentProperties(0, 24, blockedWords));
    }

    @Test
    void check_blankContent_returnsRejected() {
        ModerationResult result = service(List.of()).check("   ");
        assertThat(result.rejected()).isTrue();
        assertThat(result.reason()).isEqualTo("empty");
    }

    @Test
    void check_exceededLength_returnsRejected() {
        String tooLong = "a".repeat(2201);
        ModerationResult result = service(List.of()).check(tooLong);
        assertThat(result.rejected()).isTrue();
        assertThat(result.reason()).isEqualTo("too_long");
    }

    @Test
    void check_blockedWord_returnsRejected() {
        ModerationResult result = service(List.of("spammy")).check("this is SpAmMy content");
        assertThat(result.rejected()).isTrue();
        assertThat(result.reason()).isEqualTo("blocked_word");
    }

    @Test
    void check_repeatedToken_returnsSpam() {
        ModerationResult result = service(List.of()).check("buy buy buy buy buy");
        assertThat(result.rejected()).isTrue();
        assertThat(result.reason()).isEqualTo("spam");
    }

    @Test
    void check_validContent_returnsApproved() {
        ModerationResult result = service(List.of("blocked")).check("a perfectly fine comment");
        assertThat(result.rejected()).isFalse();
        assertThat(result.reason()).isNull();
    }
}
