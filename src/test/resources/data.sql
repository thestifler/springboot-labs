DELETE FROM tasks;

INSERT INTO tasks (id, description, status, create_at, update_at) 
VALUES (1, 'Create the Controller Test', 'INPROGRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO tasks (id, description, status, create_at, update_at) 
VALUES (2, 'Create the Controller', 'INPROGRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO tasks (id, description, status, create_at, update_at) 
VALUES (3, 'Create the Service Test', 'INPROGRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO tasks (id, description, status, create_at, update_at) 
VALUES (4, 'Create the Service', 'INPROGRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO tasks (id, description, status, create_at, update_at) 
VALUES (5, 'Create the Repository Test', 'INPROGRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

ALTER TABLE tasks ALTER COLUMN id RESTART WITH 6;