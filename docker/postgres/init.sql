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
  deleted BOOLEAN NOT NULL
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
  position INTEGER NOT NULL,
  document_id TEXT NOT NULL,
  is_group BOOLEAN NOT NULL,
  has_inner_steps BOOLEAN NOT NULL,
  background_color INTEGER,
  tags TEXT NOT NULL,
  spans TEXT NOT NULL,
  link_to_document TEXT
);

CREATE TABLE user_entity (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  created_at BIGINT NOT NULL,
  email TEXT NOT NULL UNIQUE,
  password TEXT NOT NULL,
  salt TEXT NOT NULL,
  enabled BOOLEAN NOT NULL
);

CREATE TABLE company_entity (
  domain TEXT PRIMARY KEY,
  name TEXT NOT NULL
);

CREATE TABLE workspace_entity (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  icon TEXT,
  icon_tint INTEGER
);

CREATE TABLE folder_entity (
  id TEXT PRIMARY KEY,
  parent_id TEXT NOT NULL,
  workspace_id TEXT NOT NULL,
  title TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  last_updated_at BIGINT NOT NULL,
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

