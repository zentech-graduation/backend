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
        public static final String CHANGE_PASSWORD = "/change-password";
        public static final String OAUTH2_CALLBACK = "/oauth2/callback/{provider}";
        public static final String OAUTH2_EXCHANGE = "/oauth2/exchange";
    }

    public static final class Users {
        private Users() {}

        public static final String ROOT = API_V1 + "/users";
        public static final String ME = "/me";
        public static final String ME_AVATAR = "/me/avatar";
        public static final String BY_ID = "/{userId}";
        public static final String SEARCH = "/search";
        public static final String SUGGESTIONS = "/suggestions";
        public static final String ME_SETTINGS = "/me/settings";
    }

    public static final class Posts {
        private Posts() {}

        public static final String ROOT = API_V1 + "/posts";
        public static final String BY_ID = "/{postId}";
        public static final String LIKE = "/{postId}/like";
        public static final String SAVE = "/{postId}/save";
        public static final String COMMENTS = "/{postId}/comments";
        public static final String MEDIA = "/{postId}/media";
        public static final String FEED = "/feed";
        public static final String EXPLORE = "/explore";
        public static final String USER_POSTS = "/user/{userId}";
    }

    public static final class Comments {
        private Comments() {}

        public static final String ROOT = API_V1 + "/comments";
        public static final String BY_ID = "/{commentId}";
        public static final String LIKE = "/{commentId}/like";
        public static final String REPLIES = "/{commentId}/replies";
    }

    public static final class Stories {
        private Stories() {}

        public static final String ROOT = API_V1 + "/stories";
        public static final String BY_ID = "/{storyId}";
        public static final String USER_STORIES = "/user/{userId}";
        public static final String FEED = "/feed";
        public static final String VIEWS = "/{storyId}/views";
    }

    public static final class Social {
        private Social() {}

        public static final String ROOT = API_V1 + "/social";
        public static final String FOLLOW = "/follow/{targetUserId}";
        public static final String UNFOLLOW = "/unfollow/{targetUserId}";
        public static final String FOLLOWERS = "/{userId}/followers";
        public static final String FOLLOWING = "/{userId}/following";
        public static final String FOLLOW_REQUESTS = "/follow-requests";
        public static final String FOLLOW_REQUEST_RESPOND = "/follow-requests/{requesterId}";
        public static final String BLOCK = "/block/{targetUserId}";
        public static final String UNBLOCK = "/unblock/{targetUserId}";
    }

    public static final class Messages {
        private Messages() {}

        public static final String ROOT = API_V1 + "/conversations";
        public static final String BY_ID = "/{conversationId}";
        public static final String CONVERSATION_MESSAGES = "/{conversationId}/messages";
        public static final String MESSAGE_BY_ID = "/{conversationId}/messages/{messageId}";
        public static final String PARTICIPANTS = "/{conversationId}/participants";
    }

    public static final class Notifications {
        private Notifications() {}

        public static final String ROOT = API_V1 + "/notifications";
        public static final String MARK_READ = "/{notificationId}/read";
        public static final String MARK_ALL_READ = "/read-all";
    }

    public static final class Hashtags {
        private Hashtags() {}

        public static final String ROOT = API_V1 + "/hashtags";
        public static final String BY_NAME = "/{name}";
        public static final String POSTS = "/{name}/posts";
        public static final String TRENDING = "/trending";
    }

    public static final class Media {
        private Media() {}

        public static final String ROOT = API_V1 + "/media";
        public static final String UPLOAD = "/upload";
        public static final String UPLOAD_COMPLETE = "/upload-complete";
        public static final String BY_ID = "/{mediaId}";
    }

    public static final class Reports {
        private Reports() {}

        public static final String ROOT = API_V1 + "/reports";
        public static final String BY_ID = "/{reportId}";
    }

    public static final class Admin {
        private Admin() {}

        public static final String ROOT = ADMIN;
        public static final String USERS = ADMIN + "/users";
        public static final String USER_BY_ID = ADMIN + "/users/{userId}";
        public static final String REPORTS = ADMIN + "/reports";
        public static final String REPORT_BY_ID = ADMIN + "/reports/{reportId}";
        public static final String ACTIONS = ADMIN + "/actions";
    }
}
