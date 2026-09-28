import { migrateDiscovery } from './discovery-migration.js';
import { pool } from './db.js';
await pool.query(`
CREATE TABLE IF NOT EXISTS users (
 id uuid PRIMARY KEY, username varchar(24) NOT NULL UNIQUE, email text NOT NULL UNIQUE,
 password_hash text NOT NULL, display_name varchar(50) NOT NULL, bio varchar(150) NOT NULL DEFAULT '',
 location varchar(80) NOT NULL DEFAULT '', website varchar(200) NOT NULL DEFAULT '', avatar_url text,
 created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE users ADD COLUMN IF NOT EXISTS banner_url text;
CREATE TABLE IF NOT EXISTS sessions (token_hash text PRIMARY KEY, user_id uuid NOT NULL REFERENCES users ON DELETE CASCADE, expires_at timestamptz NOT NULL);
CREATE INDEX IF NOT EXISTS sessions_user_idx ON sessions(user_id);
CREATE TABLE IF NOT EXISTS media (id uuid PRIMARY KEY, owner_id uuid NOT NULL REFERENCES users ON DELETE CASCADE, filename text NOT NULL, mime text NOT NULL, size bigint NOT NULL, sha256 text NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS themes (id uuid PRIMARY KEY, author_id uuid NOT NULL REFERENCES users ON DELETE CASCADE, title varchar(60) NOT NULL, description varchar(1000) NOT NULL DEFAULT '', category varchar(24) NOT NULL, tags text[] NOT NULL DEFAULT '{}', cover_url text NOT NULL, manifest jsonb NOT NULL, downloads integer NOT NULL DEFAULT 0, created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX IF NOT EXISTS themes_created_idx ON themes(created_at DESC, id);
CREATE INDEX IF NOT EXISTS themes_tags_idx ON themes USING gin(tags);
CREATE TABLE IF NOT EXISTS likes (user_id uuid REFERENCES users ON DELETE CASCADE, theme_id uuid REFERENCES themes ON DELETE CASCADE, PRIMARY KEY(user_id,theme_id));
CREATE TABLE IF NOT EXISTS collections (id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users ON DELETE CASCADE, name varchar(40) NOT NULL, UNIQUE(user_id,name));
CREATE TABLE IF NOT EXISTS saves (user_id uuid REFERENCES users ON DELETE CASCADE, theme_id uuid REFERENCES themes ON DELETE CASCADE, collection_id uuid REFERENCES collections ON DELETE CASCADE, PRIMARY KEY(user_id,theme_id,collection_id));
CREATE TABLE IF NOT EXISTS follows (follower_id uuid REFERENCES users ON DELETE CASCADE, following_id uuid REFERENCES users ON DELETE CASCADE, PRIMARY KEY(follower_id,following_id), CHECK(follower_id <> following_id));
CREATE TABLE IF NOT EXISTS reports (id uuid PRIMARY KEY, user_id uuid REFERENCES users ON DELETE SET NULL, theme_id uuid REFERENCES themes ON DELETE CASCADE, reason varchar(500) NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
`);
await migrateDiscovery(pool);
console.log('Homely: schema ready');
await pool.end();
