package com.app.modules.post.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.pagination.CursorScope;
import com.app.modules.post.enums.PostType;

class PostTypeFilterTest {

    @Test
    void parse_nullOrEmptyOrBlank_isTheUnfilteredCase() {
        assertThat(PostTypeFilter.parse(null).isEmpty()).isTrue();
        assertThat(PostTypeFilter.parse(List.of()).isEmpty()).isTrue();
        assertThat(PostTypeFilter.parse(List.of("", "   ")).isEmpty()).isTrue();
    }

    @Test
    void parse_unfiltered_keepsTheOriginalCursorScope() {
        // Cursors issued before the filter existed carry the bare tag and must keep working.
        assertThat(PostTypeFilter.parse(null).cursorScope()).isEqualTo(CursorScope.POST_USER_POSTS);
    }

    @Test
    void parse_isCaseInsensitiveAndTrims() {
        assertThat(PostTypeFilter.parse(List.of(" Image ", "VIDEO")).types())
                .containsExactly(PostType.IMAGE, PostType.VIDEO);
    }

    @Test
    void parse_differentOrderings_produceOneIdenticalScope() {
        PostTypeFilter forward = PostTypeFilter.parse(List.of("image", "video"));
        PostTypeFilter reversed = PostTypeFilter.parse(List.of("video", "image"));

        assertThat(forward.cursorScope()).isEqualTo(reversed.cursorScope());
        assertThat(forward.asDelimitedTypes()).isEqualTo(reversed.asDelimitedTypes());
    }

    @Test
    void parse_duplicates_areCollapsed() {
        PostTypeFilter filter = PostTypeFilter.parse(List.of("image", "image", "IMAGE"));

        assertThat(filter.types()).containsExactly(PostType.IMAGE);
        assertThat(filter.asDelimitedTypes()).isEqualTo("image");
    }

    @Test
    void cursorScope_isDistinctPerFilterSet() {
        // A filtered scope must differ from the unfiltered one and from any other subset, otherwise
        // a cursor could be replayed across filters and silently skip rows.
        String unfiltered = PostTypeFilter.parse(null).cursorScope();
        String image = PostTypeFilter.parse(List.of("image")).cursorScope();
        String imageVideo = PostTypeFilter.parse(List.of("image", "video")).cursorScope();

        assertThat(List.of(unfiltered, image, imageVideo)).doesNotHaveDuplicates();
    }

    @Test
    void cursorScope_neverContainsTheCodecSeparator() {
        // CursorCodec locates the scope by the first colon, so a colon in the scope would corrupt
        // it.
        assertThat(
                        PostTypeFilter.parse(List.of("image", "video", "carousel", "text"))
                                .cursorScope())
                .doesNotContain(":");
    }

    @Test
    void asDelimitedTypes_isNormalizedAndLowercase() {
        assertThat(PostTypeFilter.parse(List.of("TEXT", "image")).asDelimitedTypes())
                .isEqualTo("image,text");
    }

    @Test
    void parse_unknownValue_isRejectedNamingEveryAcceptedValue() {
        assertThatThrownBy(() -> PostTypeFilter.parse(List.of("photo")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("photo")
                .hasMessageContaining("image")
                .hasMessageContaining("video")
                .hasMessageContaining("carousel")
                .hasMessageContaining("text")
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.BAD_REQUEST);
    }

    @Test
    void parse_oneBadValueAmongGoodOnes_stillRejects() {
        assertThatThrownBy(() -> PostTypeFilter.parse(List.of("image", "photo")))
                .isInstanceOf(AppException.class);
    }
}
