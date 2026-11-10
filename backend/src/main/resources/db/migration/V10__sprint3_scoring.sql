-- Sprint 3 / E6-D: score submission, 30-minute edit window and audit trail.
ALTER TABLE matches
  ADD COLUMN score_submitted_at TIMESTAMPTZ,
  ADD COLUMN score_edit_deadline TIMESTAMPTZ;

-- match_games already stores per-set scores; this table keeps before/after snapshots.
CREATE TABLE match_score_logs (
  id UUID PRIMARY KEY,
  match_id UUID NOT NULL REFERENCES matches(id),
  change_type VARCHAR(20) NOT NULL,
  before_json JSONB,
  after_json JSONB,
  operator_user_id UUID REFERENCES users(id),
  reason VARCHAR(500),
  idempotency_key VARCHAR(80),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_match_score_logs_match ON match_score_logs(match_id, created_at);
CREATE UNIQUE INDEX uq_match_score_logs_idempotency
  ON match_score_logs(match_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;

CREATE TABLE match_score_change_requests (
  id UUID PRIMARY KEY,
  match_id UUID NOT NULL REFERENCES matches(id),
  requested_by UUID NOT NULL REFERENCES users(id),
  match_version BIGINT NOT NULL,
  reason VARCHAR(500) NOT NULL,
  before_json JSONB NOT NULL,
  after_json JSONB NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  reviewed_by UUID REFERENCES users(id),
  reviewed_at TIMESTAMPTZ,
  review_note VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_match_score_change_requests_status
  ON match_score_change_requests(status, created_at);
CREATE UNIQUE INDEX uq_pending_score_change
  ON match_score_change_requests(match_id)
  WHERE status = 'PENDING';
