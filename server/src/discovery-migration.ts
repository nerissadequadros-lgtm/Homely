import type { Pool } from 'pg';
export async function migrateDiscovery(pool:Pool) {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS schema_migrations(version text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now());
    CREATE TABLE IF NOT EXISTS discovery_preferences(user_id uuid PRIMARY KEY REFERENCES users ON DELETE CASCADE,
      timezone text NOT NULL DEFAULT 'America/Sao_Paulo', enabled boolean NOT NULL DEFAULT true,
      profile jsonb NOT NULL DEFAULT '{"weights":{},"seen":{},"version":1}', updated_at timestamptz NOT NULL DEFAULT now());
    CREATE TABLE IF NOT EXISTS behavior_events(id uuid PRIMARY KEY,user_id uuid NOT NULL REFERENCES users ON DELETE CASCADE,
      kind text NOT NULL,theme_id uuid REFERENCES themes ON DELETE SET NULL,context text,duration integer NOT NULL DEFAULT 0,created_at timestamptz NOT NULL DEFAULT now());
    CREATE INDEX IF NOT EXISTS behavior_user_time_idx ON behavior_events(user_id,created_at DESC);
    CREATE TABLE IF NOT EXISTS discovery_notifications(id uuid PRIMARY KEY,user_id uuid NOT NULL REFERENCES users ON DELETE CASCADE,
      local_date date NOT NULL,available_at timestamptz NOT NULL,minute integer NOT NULL,template integer NOT NULL,
      title text NOT NULL,theme_ids uuid[] NOT NULL DEFAULT '{}',read_at timestamptz,opened_at timestamptz,UNIQUE(user_id,local_date));
    CREATE INDEX IF NOT EXISTS discovery_due_idx ON discovery_notifications(user_id,available_at DESC);
    INSERT INTO schema_migrations(version) VALUES('008_discovery') ON CONFLICT DO NOTHING;
  `);
}
