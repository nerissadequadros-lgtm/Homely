import 'dotenv/config';
import { createApp } from './app.js';
import { pool } from './db.js';
const app=await createApp();
await app.listen({host:process.env.HOST||'127.0.0.1',port:Number(process.env.PORT||3187)});
for(const signal of ['SIGTERM','SIGINT'] as const) process.once(signal,async()=>{await app.close();await pool.end();process.exit(0);});
