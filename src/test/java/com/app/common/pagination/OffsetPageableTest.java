package com.app.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class OffsetPageableTest {

    @Test
    void getOffset_isTheExactOffset_notPageTimesSize() {
        // 25 is not a multiple of 10; PageRequest would floor it to page 2 and report offset 20,
        // silently repeating five rows. This is the bug the type exists to avoid.
        OffsetPageable pageable = new OffsetPageable(25, 10);

        assertThat(pageable.getOffset()).isEqualTo(25L);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(PageRequest.of(pageable.getPageNumber(), 10).getOffset()).isEqualTo(20L);
    }

    @Test
    void navigation_movesByPageSize() {
        OffsetPageable pageable = new OffsetPageable(20, 10);

        assertThat(pageable.next().getOffset()).isEqualTo(30L);
        assertThat(pageable.previousOrFirst().getOffset()).isEqualTo(10L);
        assertThat(pageable.first().getOffset()).isZero();
        assertThat(pageable.withPage(3).getOffset()).isEqualTo(30L);
        assertThat(pageable.hasPrevious()).isTrue();
    }

    @Test
    void firstPage_hasNoPrevious_andClampsAtZero() {
        OffsetPageable first = new OffsetPageable(0, 20);

        assertThat(first.hasPrevious()).isFalse();
        assertThat(first.previousOrFirst()).isSameAs(first);
        assertThat(first.getPageNumber()).isZero();
        assertThat(new OffsetPageable(5, 20).previousOrFirst().getOffset()).isZero();
    }

    @Test
    void zeroSize_doesNotDivideByZero() {
        assertThat(new OffsetPageable(10, 0).getPageNumber()).isZero();
    }
}
