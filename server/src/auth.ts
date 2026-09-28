import { randomBytes, scrypt as scryptCb, timingSafeEqual, createHash } from 'node:crypto';
import { promisify } from 'node:util';
import type { FastifyRequest } from 'fastify';
import { query } from './db.js';
const scrypt = promisify(scryptCb);
export const digest = (text: string) => createHash('sha256').update(text).digest('hex');
export async function hashPassword(password: string) { const salt = randomBytes(16).toString('hex'); const key = await scrypt(password, salt, 64) as Buffer; return `${salt}:${key.toString('hex')}`; }
export async function verifyPassword(password: string, hash: string) { const [salt, encoded] = hash.split(':'); if (!salt || !encoded) return false; const key = await scrypt(password,salt,64) as Buffer; const expected=Buffer.from(encoded,'hex'); return expected.length===key.length && timingSafeEqual(expected,key); }
export async function newSession(userId: string) { const token = randomBytes(32).toString('base64url'); await query('INSERT INTO sessions VALUES($1,$2,now()+interval \'30 days\')',[digest(token),userId]); return token; }
export async function identity(req: FastifyRequest, required = true): Promise<string | null> {
 const auth = req.headers.authorization;
 if (!auth?.startsWith('Bearer ')) { if(required) throw Object.assign(new Error('Entre na sua conta para continuar.'),{statusCode:401}); return null; }
 const rows=await query('SELECT user_id FROM sessions WHERE token_hash=$1 AND expires_at>now()',[digest(auth.slice(7))]);
 if(!rows[0] && required) throw Object.assign(new Error('Sua sessão expirou. Entre novamente.'),{statusCode:401});
 return rows[0]?.user_id ?? null;
}
