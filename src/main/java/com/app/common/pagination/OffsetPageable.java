package com.app.common.pagination;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * A {@link Pageable} whose offset is the exact cursor offset rather than {@code page * size}.
 *
 * <p>{@code PageRequest} derives its offset by multiplying page number by page size, which
 * truncates any offset not divisible by the page size — so a client that varies its limit between
 * requests silently skips or repeats results. Offset-cursor paging needs the offset carried through
 * unchanged.
 *
 * @param offset zero-based row offset
 * @param size requested page size
 */
public record OffsetPageable(int offset, int size) implements Pageable {

    @Override
    public int getPageNumber() {
        return size > 0 ? offset / size : 0;
    }

    @Override
    public int getPageSize() {
        return size;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return Sort.unsorted();
    }

    @Override
    public Pageable next() {
        return new OffsetPageable(offset + size, size);
    }

    @Override
    public Pageable previousOrFirst() {
        return offset > 0 ? new OffsetPageable(Math.max(0, offset - size), size) : this;
    }

    @Override
    public Pageable first() {
        return new OffsetPageable(0, size);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetPageable(pageNumber * size, size);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
