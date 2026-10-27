ALTER TABLE community_posts
    ADD COLUMN marketplace_condition VARCHAR(40),
    ADD COLUMN delivery_method VARCHAR(30),
    ADD COLUMN moderation_previous_status VARCHAR(20),
    ADD COLUMN market_rules_accepted_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_market_hidden_previous_status CHECK (
        status <> 'MODERATION_HIDDEN' OR moderation_previous_status IS NOT NULL
    );

CREATE TABLE marketplace_reports (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES community_posts(id),
    reporter_user_id UUID NOT NULL REFERENCES users(id),
    reason VARCHAR(30) NOT NULL,
    details VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewer_user_id UUID REFERENCES users(id),
    review_action VARCHAR(20),
    review_note VARCHAR(500),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_marketplace_reports_reason CHECK (
        reason IN ('SUSPECTED_FRAUD', 'PROHIBITED_GOODS', 'MISLEADING', 'HARASSMENT', 'PRIVACY', 'OTHER')
    ),
    CONSTRAINT chk_marketplace_reports_status CHECK (status IN ('PENDING', 'REVIEWED')),
    CONSTRAINT chk_marketplace_reports_action CHECK (review_action IS NULL OR review_action IN ('HIDE', 'DISMISS')),
    CONSTRAINT chk_marketplace_reports_review_fields CHECK (
        (status = 'PENDING' AND reviewer_user_id IS NULL AND review_action IS NULL AND reviewed_at IS NULL)
        OR
        (status = 'REVIEWED' AND reviewer_user_id IS NOT NULL AND review_action IS NOT NULL AND reviewed_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_marketplace_reports_pending_reporter
    ON marketplace_reports(post_id, reporter_user_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_marketplace_reports_status_created
    ON marketplace_reports(status, created_at);

CREATE INDEX idx_marketplace_posts_moderation
    ON community_posts(post_type, status, updated_at DESC)
    WHERE post_type = 'MARKETPLACE';
