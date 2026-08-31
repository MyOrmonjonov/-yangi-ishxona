ALTER TABLE workspaces ADD COLUMN next_task_sequence BIGINT NOT NULL DEFAULT 1;
ALTER TABLE tasks ADD COLUMN sequence_number BIGINT;

WITH numbered AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY workspace_id ORDER BY created_at, id) AS rn
    FROM tasks
)
UPDATE tasks t SET sequence_number = numbered.rn
FROM numbered WHERE numbered.id = t.id;

ALTER TABLE tasks ALTER COLUMN sequence_number SET NOT NULL;
ALTER TABLE tasks ADD CONSTRAINT uq_tasks_workspace_sequence UNIQUE (workspace_id, sequence_number);

UPDATE workspaces w SET next_task_sequence = COALESCE(
    (SELECT MAX(sequence_number) + 1 FROM tasks WHERE workspace_id = w.id), 1);

ALTER TABLE users ADD COLUMN last_bot_workspace_id BIGINT REFERENCES workspaces(id);
