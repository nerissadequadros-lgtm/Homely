import 'dotenv/config';
import pg from 'pg';
export const pool = new pg.Pool({ connectionString: process.env.DATABASE_URL, max: 10 });
export async function query<T extends pg.QueryResultRow = any>(sql: string, args: unknown[] = []) { return (await pool.query<T>(sql, args)).rows; }
