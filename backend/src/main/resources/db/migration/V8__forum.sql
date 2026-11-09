CREATE TABLE forum_posts (
  id UUID PRIMARY KEY,
  author_user_id UUID NOT NULL REFERENCES users(id),
  category VARCHAR(40) NOT NULL,
  title VARCHAR(160) NOT NULL,
  content TEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED'
    CHECK (status IN ('PUBLISHED', 'HIDDEN', 'DELETED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_forum_posts_visible ON forum_posts(created_at DESC, id DESC)
  WHERE status = 'PUBLISHED';
CREATE INDEX idx_forum_posts_category ON forum_posts(category, created_at DESC, id DESC)
  WHERE status = 'PUBLISHED';

CREATE TABLE forum_comments (
  id UUID PRIMARY KEY,
  post_id UUID NOT NULL REFERENCES forum_posts(id),
  author_user_id UUID NOT NULL REFERENCES users(id),
  content TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_forum_comments_visible ON forum_comments(post_id, created_at, id)
  WHERE deleted_at IS NULL;

CREATE TABLE forum_reports (
  id UUID PRIMARY KEY,
  post_id UUID NOT NULL REFERENCES forum_posts(id),
  reporter_user_id UUID NOT NULL REFERENCES users(id),
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING', 'DISMISSED', 'HIDDEN')),
  reviewed_by UUID REFERENCES users(id),
  reviewed_at TIMESTAMPTZ,
  review_note VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (post_id, reporter_user_id)
);
CREATE INDEX idx_forum_reports_status ON forum_reports(status, created_at DESC, id DESC);
