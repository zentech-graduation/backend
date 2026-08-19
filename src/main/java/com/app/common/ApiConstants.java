package com.app.common;

/** API path constants. Base prefix {@code /api/v1}. */
public final class ApiConstants {

    private ApiConstants() {}

    public static final String API_V1 = "/api/v1";
    private static final String ADMIN = API_V1 + "/admin";

    public static final class Auth {
        private Auth() {}

        public static final String ROOT = API_V1 + "/auth";
        public static final String REGISTER = "/register";
        public static final String LOGIN = "/login";
        public static final String LOGOUT = "/logout";
        public static final String REFRESH = "/refresh";
        public static final String FORGOT_PASSWORD = "/forgot-password";
        public static final String RESET_PASSWORD = "/reset-password";
        public static final String VERIFY_EMAIL = "/verify-email";
        public static final String RESEND_VERIFY = "/verify-email/resend";
        public static final String OAUTH2_EXCHANGE = "/oauth2/exchange";
        public static final String WS_TICKET = "/ws-ticket";
    }

    public static final class Users {
        private Users() {}

        public static final String ROOT = API_V1 + "/users";
        public static final String ME = "/me";
        public static final String BY_ID = "/{userId}";
        public static final String BY_USERNAME = "/by-username/{username}";
        public static final String SEARCH = "/search";
        public static final String ME_SETTINGS = "/me/settings";
        public static final String ME_WARNINGS = "/me/warnings";
    }

    public static final class Posts {
        private Posts() {}

        public static final String ROOT = API_V1 + "/posts";
        public static final String BY_ID = "/{postId}";
        public static final String STATUS = "/{postId}/status";
        public static final String HISTORY = "/{postId}/history";
        public static final String LIKE = "/{postId}/like";
        public static final String LIKES = "/{postId}/likes";
        public static final String SAVE = "/{postId}/save";
        public static final String SAVED = "/saved";
        public static final String LIKED = "/liked";
        public static final String SEARCH = "/search";
        public static final String COMMENTS = "/{postId}/comments";
        public static final String FEED = "/feed";
        public static final String USER_POSTS = "/user/{userId}";
    }

    public static final class Comments {
        private Comments() {}

        public static final String ROOT = API_V1 + "/comments";
        public static final String BY_ID = "/{commentId}";
        public static final String LIKE = "/{commentId}/like";
        public static final String REPLIES = "/{commentId}/replies";
        public static final String DELETION_SCOPE = "/{commentId}/deletion-scope";
    }

    public static final class Stories {
        private Stories() {}

        public static final String ROOT = API_V1 + "/stories";
        public static final String BY_ID = "/{storyId}";
        public static final String USER_STORIES = "/user/{userId}";
        public static final String FEED = "/feed";
        public static final String VIEWS = "/{storyId}/views";
        public static final String LIKES = "/{storyId}/likes";
    }

    public static final class Social {
        private Social() {}

        public static final String ROOT = API_V1 + "/social";
        public static final String FOLLOW = "/follow/{targetUserId}";
        public static final String FOLLOWERS = "/users/{userId}/followers";
        public static final String FOLLOWING = "/users/{userId}/following";
        public static final String FOLLOW_REQUESTS = "/follow-requests";
        public static final String FOLLOW_REQUEST_APPROVE =
                "/follow-requests/{requesterId}/approve";
        public static final String FOLLOW_REQUEST_REJECT = "/follow-requests/{requesterId}/reject";
        public static final String BLOCK = "/block/{targetUserId}";
        public static final String BLOCKED = "/blocked";
    }

    public static final class Messages {
        private Messages() {}

        public static final String ROOT = API_V1 + "/conversations";
        public static final String BY_ID = "/{conversationId}";
        public static final String CONVERSATION_MESSAGES = "/{conversationId}/messages";
        public static final String MESSAGE_BY_ID = "/{conversationId}/messages/{messageId}";
        public static final String READ = "/{conversationId}/read";
        public static final String UNREAD = "/{conversationId}/unread";
        public static final String UNREAD_COUNT = "/unread-count";
        public static final String PIN = "/{conversationId}/pin";
        public static final String MUTE = "/{conversationId}/mute";
        public static final String NICKNAME = "/{conversationId}/nickname";
    }

    public static final class Notifications {
        private Notifications() {}

        public static final String ROOT = API_V1 + "/notifications";
        public static final String MARK_READ = "/{notificationId}/read";
        public static final String MARK_ALL_READ = "/read-all";
        public static final String UNREAD_COUNT = "/unread-count";
    }

    public static final class Hashtags {
        private Hashtags() {}

        public static final String ROOT = API_V1 + "/hashtags";
        public static final String TRENDING = "/trending";
        public static final String SEARCH = "/search";
    }

    public static final class Media {
        private Media() {}

        public static final String ROOT = API_V1 + "/media";
        public static final String UPLOAD = "/upload";
        public static final String UPLOAD_COMPLETE = "/upload-complete";
        public static final String CONSTRAINTS = "/constraints";
    }

    public static final class Reports {
        private Reports() {}

        public static final String ROOT = API_V1 + "/reports";
        public static final String PENDING = "/pending";
        public static final String BY_ID = "/{reportId}";
        public static final String STATUS = "/{reportId}/status";
    }

    public static final class Admin {
        private Admin() {}

        public static final String ROOT = ADMIN;
        public static final String USERS = "/users";
        // Declared before USER_BY_ID for readability only. The literal segment wins over the
        // "/users/{userId}" template in Spring MVC's pattern comparator regardless of declaration
        // order, and both live under the ADMIN-only "/api/v1/admin/users/**" matcher, so no
        // authorization outcome depends on which one matches.
        public static final String USER_SEARCH = "/users/search";
        public static final String USER_BY_ID = "/users/{userId}";
        public static final String USER_ROLE = "/users/{userId}/role";
        public static final String USER_FORCE_LOGOUT = "/users/{userId}/force-logout";
        public static final String BAN_USER = "/users/{userId}/ban";
        public static final String UNBAN_USER = "/users/{userId}/unban";
        public static final String SUSPEND_USER = "/users/{userId}/suspend";
        public static final String UNSUSPEND_USER = "/users/{userId}/unsuspend";
        public static final String REMOVE_POST = "/posts/{postId}/remove";
        public static final String RESTORE_POST = "/posts/{postId}/restore";
        public static final String REMOVE_COMMENT = "/comments/{commentId}/remove";
        public static final String RESTORE_COMMENT = "/comments/{commentId}/restore";
        public static final String RESOLVE_REPORT = "/reports/{reportId}/resolve";
        public static final String DISMISS_REPORT = "/reports/{reportId}/dismiss";
        public static final String ESCALATE_REPORT = "/reports/{reportId}/escalate";
        // Literal segments, so no template can shadow it whatever the matcher ordering.
        public static final String ESCALATED_REPORT_COUNT = "/reports/escalated/count";
        public static final String ACTIONS = "/actions";
        public static final String ACTION_BY_ID = "/actions/{actionId}";
        // Deliberately not "/users/{userId}/actions": the "/users/**" sub-tree is reserved for the
        // ADMIN-only matcher, and audit reads stay available to moderators. Keeping the path split
        // structural means no matcher-ordering subtlety decides authorization.
        public static final String ACTIONS_FOR_USER = "/actions/for-user/{userId}";
        // Outside the "/users/**" sub-tree for the same structural reason as ACTIONS_FOR_USER: that
        // sub-tree is reserved for the ADMIN-only matcher, and warning and violation reads are
        // moderator work. Putting them under "/users/" would mean adding exceptions ahead of that
        // matcher, which is precisely the ordering subtlety the split exists to avoid.
        public static final String WARN_USER = "/warnings/for-user/{userId}";
        public static final String VIOLATIONS_FOR_USER = "/violations/for-user/{userId}";
        public static final String REVOKE_WARNING = "/warnings/{warningId}";
        public static final String REVOKE_STRIKE = "/strikes/{strikeId}";
    }
}
