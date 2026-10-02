-- Migracion V3: los roles dejan de ser un texto guardado junto al usuario y
-- pasan a ser una tabla con identidad propia.
--
-- Que estaba mal (V2)
-- ------------------
-- La tabla user_roles guardaba (user_id, role), con 'role' siendo el NOMBRE del
-- rol en texto. Eso mezclaba dos cosas en la misma tabla:
--
--   1. El catalogo de roles (que roles existen) estaba duplicado por cada
--      usuario: 'CUSTOMER' estaba escrito una vez por cada cliente.
--   2. No habia forma de consultar un rol: no se podia preguntar "quien tiene el
--      rol TELLER" ni auditar quien puede hacer algo, porque el nombre no era una
--      clave.
--
-- Como queda (V3)
-- ---------------
--   roles        -> catalogo: id propio + name unico. Una fila por rol, sembrada
--                   por esta migracion.
--   user_roles   -> tabla intermedia: (user_id, role_id), ManyToMany de JPA.
--
-- Con esa forma, dar de alta un usuario NO crea el rol: el alta solo escribe en
-- user_roles una fila con el id del rol que ya existe. Añadir un rol al catalogo
-- es una migracion nueva, no un alta de usuario.
--
-- Compatibilidad: los ids de los roles se escriben aqui como literales porque la
-- tabla tiene que estar sembrada antes de que exista la primera fila de
-- user_roles, y no se puede usar un UUID aleatorio en una migracion que debe
-- devolver el mismo resultado en PostgreSQL y en H2 (los tests que no usan
-- Testcontainers). Anadir un rol al enum Role obliga a añadir su INSERT aqui, en
-- una migracion nueva; el test UserPersistenceIT.todoRolDelDominioExisteEnLaTabla
-- falla si se olvida.
--
-- La copia de datos se hace con una tabla intermedia en vez de con ALTER TABLE
-- ... DROP COLUMN porque la clave primaria de V2 no tenia nombre explicito, y su
-- nombre autogenerado es distinto en H2 y en PostgreSQL: un DROP CONSTRAINT
-- portable habria tenido que adivinarlo.

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    CONSTRAINT uk_roles_name UNIQUE (name)
);

INSERT INTO roles (id, name) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'CUSTOMER'),
    ('a0000000-0000-0000-0000-000000000002', 'TELLER'),
    ('a0000000-0000-0000-0000-000000000003', 'ADMIN');

CREATE TABLE user_roles_nueva (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL
);

-- El JOIN es lo que traduce el nombre que habia en V2 al id del catalogo. Una
-- fila cuyo nombre no exista en 'roles' se perderia en silencio (el INNER JOIN no
-- la copia); con el enum cerrado eso no puede ocurrir, porque un nombre que no
-- es un Role valido ya reventaba al leer la tabla.
INSERT INTO user_roles_nueva (user_id, role_id)
SELECT ur.user_id, r.id
FROM user_roles ur
JOIN roles r ON r.name = ur.role;

DROP TABLE user_roles;

ALTER TABLE user_roles_nueva RENAME TO user_roles;

-- La clave primaria compuesta sigue impidiendo el mismo rol dos veces para un
-- usuario, y ahora la FK a roles hace que no se pueda apuntar a un rol que no
-- esta en el catalogo.
ALTER TABLE user_roles ADD CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id);
ALTER TABLE user_roles
    ADD CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE user_roles
    ADD CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id);

-- El indice por user_id es el que hace rapida la carga de roles al autenticar
-- (el @ManyToMany con FetchType.EAGER lee por ahi). El de role_id sirve para lo
-- contrario: quien tiene un rol concreto.
CREATE INDEX idx_user_roles_user_id ON user_roles (user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);