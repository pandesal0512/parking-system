-- Users of the system. Staff accounts (manager / super admin) have user_type = NULL.
CREATE TABLE users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    username              VARCHAR(50)  NOT NULL,
    full_name             VARCHAR(120) NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    user_type             VARCHAR(20)  NULL,
    school_id             VARCHAR(30)  NULL,
    role                  VARCHAR(20)  NOT NULL,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE,
    warning_count         INT          NOT NULL DEFAULT 0,
    suspended_until       DATETIME(6)  NULL,
    registration_banned   BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          DATETIME(6)  NULL,
    created_at            DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username)
);
