CREATE TABLE users
(
    id            UUID                     NOT NULL,
    email         VARCHAR(254)             NOT NULL,
    password_hash VARCHAR(100)             NOT NULL,
    display_name  VARCHAR(100)             NOT NULL,
    role          VARCHAR(30)              NOT NULL,
    version       BIGINT                   NOT NULL DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);
