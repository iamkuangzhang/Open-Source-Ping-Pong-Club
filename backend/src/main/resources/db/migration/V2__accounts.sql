CREATE TABLE users (
    id UUID PRIMARY KEY,
    account VARCHAR(32) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_users_status CHECK (status IN ('NORMAL', 'DISABLED'))
);

CREATE TABLE user_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    nickname VARCHAR(40) NOT NULL,
    real_name VARCHAR(40),
    avatar_url VARCHAR(500),
    student_number VARCHAR(40),
    college VARCHAR(100),
    major VARCHAR(100),
    grade VARCHAR(40),
    phone VARCHAR(30),
    wechat VARCHAR(100),
    qq VARCHAR(30),
    bio VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
