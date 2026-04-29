-- =============================================
-- Retry fields for notifications table
-- =============================================
ALTER TABLE notifications ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE notifications ADD COLUMN last_retry_at TIMESTAMP;
ALTER TABLE notifications ADD COLUMN recipient_email VARCHAR(255);
ALTER TABLE notifications ADD COLUMN email_template_name VARCHAR(100);
ALTER TABLE notifications ADD COLUMN email_template_variables TEXT;

CREATE INDEX idx_notifications_failed_retry ON notifications(status, retry_count)
    WHERE status = 'FAILED';

-- =============================================
-- Notification preferences table
-- =============================================
CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    email_enabled BOOLEAN NOT NULL DEFAULT true,
    push_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(user_id, notification_type)
);

CREATE INDEX idx_notification_prefs_user ON notification_preferences(user_id);
