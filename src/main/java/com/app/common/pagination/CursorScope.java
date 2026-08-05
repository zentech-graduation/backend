package com.app.common.pagination;

/**
 * Short, stable, non-secret scope tags embedded in every {@link CursorCodec} cursor.
 *
 * <p>One constant per issuing endpoint. Renaming a constant's value is a breaking change for any
 * cursor already in flight; adding a new one is always safe.
 */
public final class CursorScope {

    private CursorScope() {}

    public static final String ADMIN_ACTIONS = "adma";
    public static final String ADMIN_ACTIONS_FOR_USER = "admu";
    public static final String COMMENTS_TOP_LEVEL = "cmt";
    public static final String COMMENT_REPLIES = "rpl";
    public static final String CONVERSATIONS = "cnv";
    public static final String NOTIFICATIONS = "ntf";
    public static final String POST_USER_POSTS = "pst";
    public static final String POST_FEED = "feed";
    public static final String POST_EDIT_HISTORY = "edh";
    public static final String POST_LIKERS = "lik";
    public static final String POST_SAVES = "sav";
    public static final String REPORTS = "rpt";
    public static final String PENDING_REPORTS = "prpt";
    public static final String SOCIAL_FOLLOWERS = "flw";
    public static final String SOCIAL_FOLLOWING = "flg";
    public static final String SOCIAL_BLOCKED = "blk";
    public static final String SOCIAL_PENDING_REQUESTS = "frq";
    public static final String STORY_VIEWERS = "stv";
}
