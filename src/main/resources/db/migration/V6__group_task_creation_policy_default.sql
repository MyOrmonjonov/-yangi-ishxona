UPDATE groups SET task_creation_policy = 'EVERYONE' WHERE task_creation_policy = 'MANAGERS';

ALTER TABLE groups ALTER COLUMN task_creation_policy SET DEFAULT 'EVERYONE';
