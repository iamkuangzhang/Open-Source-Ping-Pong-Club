ALTER TABLE community_posts
  ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
  ADD COLUMN max_participants INTEGER,
  ADD COLUMN event_time TIMESTAMPTZ,
  ADD COLUMN location VARCHAR(200),
  ADD COLUMN contact VARCHAR(120),
  ADD COLUMN price VARCHAR(60);

ALTER TABLE community_posts
  ADD CONSTRAINT chk_community_posts_visibility CHECK (visibility IN ('PUBLIC','MEMBERS_ONLY')),
  ADD CONSTRAINT chk_community_posts_max_participants CHECK (max_participants IS NULL OR max_participants > 0);

CREATE TABLE meetup_participants (
  id UUID PRIMARY KEY,
  post_id UUID NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id),
  joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(post_id, user_id)
);

CREATE INDEX idx_meetup_participants_post ON meetup_participants(post_id, joined_at);
