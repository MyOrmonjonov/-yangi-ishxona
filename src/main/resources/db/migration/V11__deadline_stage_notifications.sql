ALTER TABLE tasks ADD COLUMN deadline_reminder_sent_at TIMESTAMPTZ;
ALTER TABLE tasks ADD COLUMN deadline_escalated_at TIMESTAMPTZ;
