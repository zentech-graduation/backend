-- Composite (story_id, viewed_at DESC, viewer_id DESC) index so the story-viewer row-value keyset
-- comparison resolves as an exact index seek. The primary key is (story_id, viewer_id), which does
-- not order by viewed_at, so the viewer list previously had no supporting index.

CREATE INDEX idx_story_views_story_viewed_viewer
    ON story_views (story_id, viewed_at DESC, viewer_id DESC);
