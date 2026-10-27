# 数据库设计 V1.0

数据库：PostgreSQL 17  
Migration：Flyway  
主业务主键：UUID  
时间字段：TIMESTAMPTZ

Hibernate：
`spring.jpa.hibernate.ddl-auto=validate`

## 1. users

- id UUID PK
- account VARCHAR(32) UNIQUE NOT NULL
- password_hash VARCHAR(255) NOT NULL
- status VARCHAR(20) NOT NULL
- created_at TIMESTAMPTZ NOT NULL
- updated_at TIMESTAMPTZ NOT NULL

status：
- NORMAL
- DISABLED

## 2. user_profiles

- user_id UUID PK FK
- nickname VARCHAR(40) NOT NULL
- real_name VARCHAR(40)
- avatar_url VARCHAR(500)
- student_number VARCHAR(40)
- college VARCHAR(100)
- major VARCHAR(100)
- grade VARCHAR(40)
- phone VARCHAR(30)
- wechat VARCHAR(100)
- qq VARCHAR(30)
- bio VARCHAR(500)
- created_at
- updated_at

## 3. membership_applications

- id UUID PK
- user_id UUID NOT NULL
- real_name VARCHAR(40) NOT NULL
- student_number VARCHAR(40) NOT NULL
- college VARCHAR(100) NOT NULL
- major VARCHAR(100) NOT NULL
- grade VARCHAR(40) NOT NULL
- phone VARCHAR(30) NOT NULL
- reason VARCHAR(500)
- status VARCHAR(20) NOT NULL
- reviewer_user_id UUID
- review_comment VARCHAR(500)
- reviewed_at TIMESTAMPTZ
- created_at
- updated_at

同一用户最多一个 PENDING。

## 4. memberships

- id UUID PK
- user_id UUID UNIQUE NOT NULL
- member_number VARCHAR(30) UNIQUE NOT NULL
- status VARCHAR(20) NOT NULL
- joined_at TIMESTAMPTZ NOT NULL
- left_at TIMESTAMPTZ
- created_at
- updated_at

status：
- ACTIVE
- LEFT
- REMOVED

## 5. organization_roles

- id UUID PK
- code VARCHAR(40) UNIQUE NOT NULL
- name VARCHAR(40) NOT NULL

初始化：
MEMBER / ADMIN / VICE_MINISTER / MINISTER / VICE_PRESIDENT / PRESIDENT / ADVISOR

ADMIN 由 PRESIDENT / ADVISOR 任命，不授予 `ROLE_MANAGE`。

## 6. user_organization_roles

- id UUID PK
- user_id UUID NOT NULL
- role_id UUID NOT NULL
- granted_by UUID
- granted_at TIMESTAMPTZ NOT NULL
- revoked_by UUID
- revoked_at TIMESTAMPTZ

生效条件：revoked_at IS NULL。

## 7. rating_accounts

- user_id UUID PK
- current_rating INTEGER NOT NULL DEFAULT 1400
- highest_rating INTEGER NOT NULL DEFAULT 1400
- rated_matches INTEGER NOT NULL DEFAULT 0
- wins INTEGER NOT NULL DEFAULT 0
- losses INTEGER NOT NULL DEFAULT 0
- version BIGINT NOT NULL DEFAULT 0
- created_at
- updated_at

CHECK：
- current_rating >= 400
- highest_rating >= current_rating

## 8. rating_settlements

- id UUID PK
- competition_id UUID UNIQUE NOT NULL
- rule_version VARCHAR(20) NOT NULL
- status VARCHAR(20) NOT NULL
- created_at
- completed_at

## 9. rating_ledger

- id UUID PK
- user_id UUID NOT NULL
- source_type VARCHAR(20) NOT NULL
- source_id UUID
- competition_id UUID
- match_id UUID
- opponent_user_id UUID
- rating_before INTEGER
- rating_delta INTEGER NOT NULL
- rating_after INTEGER NOT NULL
- result VARCHAR(20)
- rule_version VARCHAR(20) NOT NULL
- reason VARCHAR(500)
- settlement_id UUID
- created_at TIMESTAMPTZ NOT NULL

source_type：
- INITIAL
- MATCH
- CORRECTION

MATCH 类型应保证 `(match_id, user_id)` 唯一。

## 10. competitions

- id UUID PK
- title VARCHAR(120) NOT NULL
- description TEXT
- cover_url VARCHAR(500)
- competition_type VARCHAR(20) NOT NULL
- event_type VARCHAR(20) NOT NULL
- sport_id UUID NOT NULL REFERENCES sports(id)
- format VARCHAR(30) NOT NULL
- status VARCHAR(30) NOT NULL
- registration_scope VARCHAR(20) NOT NULL
- registration_requires_review BOOLEAN NOT NULL
- max_participants INTEGER NOT NULL
- best_of INTEGER NOT NULL
- points_per_game INTEGER NOT NULL DEFAULT 11
- strict_score_validation BOOLEAN NOT NULL DEFAULT TRUE
- location VARCHAR(200) NOT NULL
- registration_start TIMESTAMPTZ
- registration_end TIMESTAMPTZ
- start_time TIMESTAMPTZ
- end_time TIMESTAMPTZ
- creator_user_id UUID NOT NULL
- rating_rule_version VARCHAR(20)
- arrangement_seed BIGINT
- version BIGINT NOT NULL DEFAULT 0
- created_at
- updated_at

competition_type：
DAILY / EXAM / RATED

event_type：
SINGLES

format：
SINGLE_ELIMINATION / ROUND_ROBIN

sport_id 指向运动类型目录；历史赛事迁移时归入 PING_PONG。

## 10.1 sports

- id UUID PK
- code VARCHAR(40) UNIQUE NOT NULL
- name VARCHAR(40) NOT NULL
- active BOOLEAN NOT NULL DEFAULT TRUE
- sort_order INTEGER NOT NULL DEFAULT 100
- created_by UUID
- created_at / updated_at TIMESTAMPTZ NOT NULL

V8 初始化 PING_PONG、BADMINTON、BASKETBALL、FOOTBALL。运动类型被赛事引用后保留其记录。

## 11. competition_entries

- id UUID PK
- competition_id UUID NOT NULL
- user_id UUID NOT NULL
- status VARCHAR(20) NOT NULL
- seed_number INTEGER
- registered_at TIMESTAMPTZ NOT NULL
- approved_at TIMESTAMPTZ
- created_at
- updated_at

UNIQUE(competition_id, user_id)

## 12. competition_stages

- id UUID PK
- competition_id UUID NOT NULL
- name VARCHAR(80) NOT NULL
- stage_type VARCHAR(20) NOT NULL
- stage_order INTEGER NOT NULL
- created_at

stage_type：
GROUP / KNOCKOUT

## 13. competition_groups

- id UUID PK
- stage_id UUID NOT NULL
- name VARCHAR(40) NOT NULL
- group_order INTEGER NOT NULL
- created_at

## 14. competition_group_members

- id UUID PK
- group_id UUID NOT NULL
- user_id UUID NOT NULL
- position INTEGER
- created_at

UNIQUE(group_id, user_id)

## 15. matches

- id UUID PK
- competition_id UUID NOT NULL
- stage_id UUID NOT NULL
- group_id UUID
- round_number INTEGER
- match_number INTEGER NOT NULL
- table_number VARCHAR(30)
- scheduled_at TIMESTAMPTZ
- started_at TIMESTAMPTZ
- completed_at TIMESTAMPTZ
- status VARCHAR(20) NOT NULL
- best_of INTEGER NOT NULL
- player_a_user_id UUID
- player_b_user_id UUID
- player_a_games INTEGER NOT NULL DEFAULT 0
- player_b_games INTEGER NOT NULL DEFAULT 0
- winner_user_id UUID
- loser_user_id UUID
- result_type VARCHAR(20)
- rating_eligible BOOLEAN NOT NULL DEFAULT FALSE
- result_reason VARCHAR(500)
- next_match_id UUID
- winner_to_slot VARCHAR(1)
- version BIGINT NOT NULL DEFAULT 0
- created_at
- updated_at

winner_to_slot：
A / B

## 16. match_games

- id UUID PK
- match_id UUID NOT NULL
- game_number INTEGER NOT NULL
- player_a_score INTEGER NOT NULL
- player_b_score INTEGER NOT NULL
- winner_user_id UUID NOT NULL
- created_at
- updated_at

UNIQUE(match_id, game_number)

## 17. announcements

- id UUID PK
- title VARCHAR(160) NOT NULL
- content TEXT NOT NULL
- author_user_id UUID NOT NULL
- visibility VARCHAR(20) NOT NULL
- is_pinned BOOLEAN NOT NULL DEFAULT FALSE
- published_at TIMESTAMPTZ
- created_at
- updated_at
- deleted_at

## 18. play_posts

- id UUID PK
- creator_user_id UUID NOT NULL
- title VARCHAR(120) NOT NULL
- description TEXT
- location VARCHAR(200) NOT NULL
- start_time TIMESTAMPTZ NOT NULL
- max_players INTEGER NOT NULL
- minimum_rating INTEGER
- maximum_rating INTEGER
- contact_type VARCHAR(30)
- contact_value VARCHAR(150)
- status VARCHAR(20) NOT NULL
- created_at
- updated_at
- deleted_at

## 19. play_post_participants

- id UUID PK
- play_post_id UUID NOT NULL
- user_id UUID NOT NULL
- status VARCHAR(20) NOT NULL
- joined_at TIMESTAMPTZ
- left_at TIMESTAMPTZ

UNIQUE(play_post_id, user_id)

## 20. market_posts

- id UUID PK
- seller_user_id UUID NOT NULL
- title VARCHAR(160) NOT NULL
- category VARCHAR(30) NOT NULL
- price NUMERIC(10,2) NOT NULL
- original_price NUMERIC(10,2)
- condition_level VARCHAR(40)
- description TEXT
- contact_type VARCHAR(30)
- contact_value VARCHAR(150)
- status VARCHAR(20) NOT NULL
- created_at
- updated_at
- deleted_at

## 21. market_post_images

- id UUID PK
- market_post_id UUID NOT NULL
- image_url VARCHAR(500) NOT NULL
- sort_order INTEGER NOT NULL

后端限制每个商品最多 9 张。

## 22. notifications

- id UUID PK
- user_id UUID NOT NULL
- type VARCHAR(40) NOT NULL
- title VARCHAR(120) NOT NULL
- content VARCHAR(1000)
- related_type VARCHAR(40)
- related_id UUID
- is_read BOOLEAN NOT NULL DEFAULT FALSE
- created_at
- read_at

## 22.1 二手信息治理（V10）

`community_posts` 新增：
- marketplace_condition VARCHAR(40)
- delivery_method VARCHAR(30)
- moderation_previous_status VARCHAR(20)：临时隐藏前的状态，恢复时还原
- market_rules_accepted_at TIMESTAMPTZ：发布时确认交易规则的时间

新增 `marketplace_reports`：
- id UUID PK
- post_id UUID NOT NULL REFERENCES community_posts(id)
- reporter_user_id UUID NOT NULL REFERENCES users(id)
- reason VARCHAR(30) NOT NULL
- details VARCHAR(500)
- status VARCHAR(20) NOT NULL：PENDING / REVIEWED
- reviewer_user_id UUID REFERENCES users(id)
- review_action VARCHAR(20)：HIDE / DISMISS
- review_note VARCHAR(500)
- reviewed_at TIMESTAMPTZ
- created_at TIMESTAMPTZ NOT NULL

同一用户对同一商品同时只能有一条待处理举报。已处理后可以重新举报。V10 新建举报索引和审核列表索引；已执行的 Migration 不得改写。

## 23. audit_logs

- id UUID PK
- operator_user_id UUID
- action VARCHAR(80) NOT NULL
- target_type VARCHAR(80) NOT NULL
- target_id UUID
- before_json JSONB
- after_json JSONB
- reason VARCHAR(500)
- created_at TIMESTAMPTZ NOT NULL

## 24. 关键索引

至少：
- memberships(user_id)
- memberships(member_number)
- memberships(status)
- membership_applications(status, created_at)
- rating_accounts(current_rating DESC)
- rating_ledger(user_id, created_at DESC)
- rating_ledger(match_id)
- competitions(status, start_time)
- competitions(competition_type)
- competition_entries(competition_id, status)
- competition_entries(user_id)
- matches(competition_id, status)
- matches(stage_id, round_number, match_number)
- matches(player_a_user_id)
- matches(player_b_user_id)
- announcements(is_pinned, published_at DESC)
- play_posts(status, start_time)
- market_posts(status, created_at DESC)
- notifications(user_id, is_read, created_at DESC)
