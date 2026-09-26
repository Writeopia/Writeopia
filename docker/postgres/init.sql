CREATE TABLE document_entity (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  created_at BIGINT NOT NULL,
  last_updated_at BIGINT NOT NULL,
  last_synced BIGINT NOT NULL,
  workspace_id TEXT NOT NULL,
  favorite BOOLEAN NOT NULL,
  parent_document_id TEXT NOT NULL,
  icon TEXT,
  icon_tint INTEGER,
  is_locked BOOLEAN NOT NULL,
  company_id TEXT NULL,
  deleted BOOLEAN NOT NULL,
  published BOOLEAN NOT NULL DEFAULT FALSE,
  header_image TEXT 
);

CREATE TABLE story_step_entity (
  id TEXT PRIMARY KEY,
  local_id TEXT NOT NULL,
  type INTEGER NOT NULL,
  parent_id TEXT,
  url TEXT,
  path TEXT,
  text TEXT,
  checked BOOLEAN NOT NULL,
  position NUMERIC NOT NULL,
  document_id TEXT NOT NULL,
  is_group BOOLEAN NOT NULL,
  has_inner_steps BOOLEAN NOT NULL,
  background_color INTEGER,
  tags TEXT NOT NULL,
  spans TEXT NOT NULL,
  link_to_document TEXT,
  last_updated_at INTEGER
);

CREATE TABLE comment_entity (
  id TEXT PRIMARY KEY,
  conversation_id TEXT NOT NULL,
  document_id TEXT NOT NULL,
  comment_position BIGINT NOT NULL,
  text TEXT NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE user_entity (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  username TEXT NOT NULL UNIQUE,
  created_at BIGINT NOT NULL,
  email TEXT NOT NULL UNIQUE,
  password TEXT NOT NULL,
  salt TEXT NOT NULL,
  confirmation_code TEXT,
  confirmation_code_expiry BIGINT,
  account_type TEXT NOT NULL DEFAULT 'FREE',
  -- EMAIL_CONFIRMATION_PENDING | ACTIVE | DELETION_PENDING - see UserStatus in the app repo.
  status TEXT NOT NULL DEFAULT 'EMAIL_CONFIRMATION_PENDING'
);

CREATE TABLE refresh_token_entity (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES user_entity(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL,
  expires_at BIGINT NOT NULL,
  created_at BIGINT NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token_entity(user_id);

CREATE TABLE company_entity (
  domain TEXT PRIMARY KEY,
  name TEXT NOT NULL
);

CREATE TABLE workspace_entity (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  icon TEXT,
  icon_tint INTEGER,
  status TEXT NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE folder_entity (
  id TEXT PRIMARY KEY,
  parent_id TEXT NOT NULL,
  workspace_id TEXT NOT NULL,
  title TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  last_updated_at BIGINT,
  last_synced_at BIGINT,
  favorite BOOLEAN NOT NULL,
  icon TEXT,
  icon_tint INTEGER
);

CREATE TABLE workspace_to_user (
    workspace_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    role TEXT NOT NULL,
    PRIMARY KEY(workspace_id, user_id)
);

CREATE TABLE user_favorite_entity (
  user_id TEXT NOT NULL,
  document_id TEXT NOT NULL,
  workspace_id TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (user_id, document_id)
);

-- Non-partitioned here for local dev simplicity. Production (Cloud SQL) uses a
-- RANGE-partitioned version, see backend/core/database/scripts/sync_event_partitioning.sql.
CREATE TABLE sync_event (
  id TEXT NOT NULL,
  workspace_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  old_parent_id TEXT,
  new_parent_id TEXT,
  created_at BIGINT NOT NULL,
  user_id TEXT NOT NULL,
  PRIMARY KEY (id, created_at)
);

CREATE TABLE workspace_tutorial_status (
  workspace_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  tutorials_created INTEGER NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL,
  PRIMARY KEY(workspace_id, user_id)
);

-- Account-deletion saga: transactional outbox. See backend/core/database/.../OutboxEvent.sq
-- and WriteopiaScripts2/db-migrations/account_deletion_saga/ for the Debezium wiring.
CREATE TABLE outbox_event (
  id TEXT PRIMARY KEY,
  aggregate_type TEXT NOT NULL,
  aggregate_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  topic TEXT NOT NULL,
  payload TEXT NOT NULL,
  created_at BIGINT NOT NULL
);

-- Account-deletion saga: one row per deletion request, keyed by user_id. See AccountDeletion.sq.
CREATE TABLE account_deletion (
  user_id TEXT PRIMARY KEY,
  user_email TEXT NOT NULL,
  user_name TEXT NOT NULL,
  status TEXT NOT NULL,
  requested_at BIGINT NOT NULL,
  workspaces_completed_at BIGINT,
  media_completed_at BIGINT,
  completed_at BIGINT
);

-- Account-deletion saga: one row per (user, workspace). See AccountDeletionWorkspace.sq.
CREATE TABLE account_deletion_workspace (
  user_id TEXT NOT NULL,
  workspace_id TEXT NOT NULL,
  action TEXT NOT NULL,
  completed BOOLEAN NOT NULL DEFAULT FALSE,
  completed_at BIGINT,
  PRIMARY KEY (user_id, workspace_id)
);

CREATE TABLE ai_usage (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  operation_type TEXT NOT NULL,
  input_tokens INTEGER NOT NULL,
  output_tokens INTEGER NOT NULL,
  total_tokens INTEGER NOT NULL,
  model TEXT NOT NULL,
  created_at BIGINT NOT NULL
);

-- Indexes for common query patterns
CREATE INDEX idx_document_workspace_id ON document_entity(workspace_id);
CREATE INDEX idx_document_parent_id ON document_entity(parent_document_id);
CREATE INDEX idx_folder_workspace_id ON folder_entity(workspace_id);
CREATE INDEX idx_folder_parent_id ON folder_entity(parent_id);
CREATE INDEX idx_story_step_document_id ON story_step_entity(document_id);
CREATE INDEX idx_comment_document_id ON comment_entity(document_id);
CREATE INDEX idx_workspace_to_user_user_id ON workspace_to_user(user_id);
CREATE INDEX idx_sync_event_workspace_id ON sync_event(workspace_id);
CREATE INDEX idx_ai_usage_user_id ON ai_usage(user_id);
CREATE INDEX idx_ai_usage_created_at ON ai_usage(created_at);
CREATE INDEX idx_account_deletion_status ON account_deletion(status);
CREATE INDEX idx_account_deletion_workspace_user_id ON account_deletion_workspace(user_id);
