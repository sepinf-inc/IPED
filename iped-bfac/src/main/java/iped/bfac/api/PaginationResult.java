package iped.bfac.api;

import java.util.List;

/**
 * Represents a paginated result from the BFAC API using cursor-based
 * pagination.
 *
 * @param <T> The type of data in the result list
 */
public class PaginationResult<T> {

    private List<T> data;
    private PaginationMeta pagination;

    public PaginationResult() {
    }

    public PaginationResult(List<T> data, PaginationMeta pagination) {
        this.data = data;
        this.pagination = pagination;
    }

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

    public PaginationMeta getPagination() {
        return pagination;
    }

    public void setPagination(PaginationMeta pagination) {
        this.pagination = pagination;
    }

    /**
     * @return true if there are more items available via pagination
     */
    public boolean hasMore() {
        return pagination != null && pagination.isHasMore();
    }

    /**
     * @return the cursor to use for the next page, or null if no more pages
     */
    public String getNextCursor() {
        return pagination != null ? pagination.getNextCursor() : null;
    }

    /**
     * @return the limit used for this page
     */
    public int getLimit() {
        return pagination != null ? pagination.getLimit() : 0;
    }

    /**
     * Represents pagination metadata from the API response.
     */
    public static class PaginationMeta {
        private int limit;
        private boolean hasMore;
        private String nextCursor;
        private String cursorField;

        public PaginationMeta() {
        }

        public PaginationMeta(int limit, boolean hasMore, String nextCursor, String cursorField) {
            this.limit = limit;
            this.hasMore = hasMore;
            this.nextCursor = nextCursor;
            this.cursorField = cursorField;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public boolean isHasMore() {
            return hasMore;
        }

        public void setHasMore(boolean hasMore) {
            this.hasMore = hasMore;
        }

        public String getNextCursor() {
            return nextCursor;
        }

        public void setNextCursor(String nextCursor) {
            this.nextCursor = nextCursor;
        }

        public String getCursorField() {
            return cursorField;
        }

        public void setCursorField(String cursorField) {
            this.cursorField = cursorField;
        }
    }
}
