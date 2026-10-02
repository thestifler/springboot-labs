-- Migracion V2: tabla de usuarios y sus roles.
--
-- El esquema lo gestiona Flyway y se valida con hibernate.ddl-auto=validate.
-- Debe ser compatible con PostgreSQL (produccion) y con H2 (tests que no usan
-- Testcontainers), por eso no se usan tipos exclusivos de PostgreSQL ni funciones
-- como gen_random_uuid(): los identificadores se generan en Java.

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- Tabla de roles. Cada rol es una fila, lo que permite anadir roles sin cambiar
-- el esquema, y el indice por user_id es el que hace rapida la carga de roles al
-- autenticar (el @ElementCollection con FetchType.EAGER lee por user_id).
CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_roles_user_id ON user_roles (user_id);
