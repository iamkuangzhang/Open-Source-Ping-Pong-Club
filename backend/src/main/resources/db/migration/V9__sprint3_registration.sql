-- Sprint 3 / E6-B: registration, waitlist, credential and mock payment.
-- Competition is the event aggregate in this codebase; registrations extend the existing entry table
-- so schedule generation keeps seeing APPROVED entries without a projection step.
ALTER TABLE competitions
  ADD COLUMN filled_slots INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN fee_cents INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN sport VARCHAR(30) NOT NULL DEFAULT 'TABLE_TENNIS';

UPDATE competitions c
SET filled_slots = (
  SELECT COUNT(*) FROM competition_entries e
  WHERE e.competition_id = c.id AND e.status IN ('PENDING', 'APPROVED')
);

ALTER TABLE competition_entries
  ADD COLUMN entry_type VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL',
  ADD COLUMN group_id UUID,
  ADD COLUMN partner_user_id UUID REFERENCES users(id),
  ADD COLUMN waitlist_no INTEGER,
  ADD COLUMN credential_no VARCHAR(40),
  ADD COLUMN qr_token VARCHAR(80),
  ADD COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
  ADD COLUMN paid_at TIMESTAMPTZ,
  ADD COLUMN refund_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
  ADD COLUMN cancelled_at TIMESTAMPTZ,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uq_competition_entries_credential ON competition_entries(credential_no)
  WHERE credential_no IS NOT NULL;
CREATE UNIQUE INDEX uq_competition_entries_qr ON competition_entries(qr_token)
  WHERE qr_token IS NOT NULL;
CREATE INDEX idx_competition_entries_waitlist
  ON competition_entries(competition_id, status, waitlist_no);

CREATE TABLE registration_members (
  id UUID PRIMARY KEY,
  entry_id UUID NOT NULL REFERENCES competition_entries(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id),
  member_role VARCHAR(20) NOT NULL,
  jersey_no INTEGER,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (entry_id, user_id)
);
CREATE UNIQUE INDEX uq_registration_members_jersey ON registration_members(entry_id, jersey_no)
  WHERE jersey_no IS NOT NULL;
CREATE INDEX idx_registration_members_user ON registration_members(user_id);

CREATE TABLE registration_payments (
  id UUID PRIMARY KEY,
  entry_id UUID NOT NULL UNIQUE REFERENCES competition_entries(id),
  amount_cents INTEGER NOT NULL,
  status VARCHAR(20) NOT NULL,
  mock_txn_no VARCHAR(40) NOT NULL UNIQUE,
  paid_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- C2 credit score (read-only for registration qualification checks).
CREATE TABLE credit_accounts (
  user_id UUID PRIMARY KEY REFERENCES users(id),
  score INTEGER NOT NULL DEFAULT 100,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_credit_score CHECK (score BETWEEN 0 AND 100)
);
