package com.app.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ViewerRelationshipResponseTest {

    @Test
    void none_isAllFalse() {
        ViewerRelationshipResponse none = ViewerRelationshipResponse.NONE;

        assertThat(none.isFollowing()).isFalse();
        assertThat(none.isFollowRequested()).isFalse();
        assertThat(none.isFollowedBy()).isFalse();
        assertThat(none.isBlocking()).isFalse();
    }

    @Test
    void construction_setsEachFieldIndependently() {
        ViewerRelationshipResponse state = new ViewerRelationshipResponse(true, false, true, false);

        assertThat(state.isFollowing()).isTrue();
        assertThat(state.isFollowRequested()).isFalse();
        assertThat(state.isFollowedBy()).isTrue();
        assertThat(state.isBlocking()).isFalse();
    }
}
