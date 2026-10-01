CREATE TABLE community_posts (
  id UUID PRIMARY KEY,
  post_type VARCHAR(20) NOT NULL,
  title VARCHAR(160) NOT NULL,
  content TEXT NOT NULL,
  author_user_id UUID NOT NULL REFERENCES users(id),
  status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
  expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_community_posts_type_created ON community_posts(post_type,status,created_at DESC);
CREATE TABLE notifications (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  notification_type VARCHAR(30) NOT NULL,
  title VARCHAR(160) NOT NULL,
  content VARCHAR(1000) NOT NULL,
  read_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notifications_user_created ON notifications(user_id,created_at DESC);
