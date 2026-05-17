ALTER TABLE conversations
    ALTER COLUMN customer_id DROP NOT NULL,
    ALTER COLUMN agent_id DROP NOT NULL;

ALTER TABLE conversations
    ADD COLUMN guest_session_id VARCHAR(128),
    ADD COLUMN conversation_type VARCHAR(20) NOT NULL DEFAULT 'AUTHENTICATED',
    ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_conversations_guest_session_id
    ON conversations(guest_session_id);
