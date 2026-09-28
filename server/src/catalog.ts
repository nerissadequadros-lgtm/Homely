import { z } from 'zod';
import type { FastifyInstance } from 'fastify';
import { query } from './db.js';
export const iconCategories=['Comunicação','Social','Foto e vídeo','Produtividade','Música e áudio','Navegação','Sistema','Jogos','Compras','Saúde'] as const;
export const iconStyles=['Minimalista','Colorido','3D','Contorno','Monocromático','Neon','Pastel','Retrô'] as const;
export function registerCatalog(app:FastifyInstance){
 app.get('/v1/icons',async(req)=>{
  const q=z.object({category:z.enum(iconCategories).optional(),style:z.enum(iconStyles).optional(),search:z.string().max(80).optional(),offset:z.coerce.number().int().min(0).max(100000).default(0),limit:z.coerce.number().int().min(1).max(60).default(30)}).parse(req.query);
  // Deduplicate the same app image reused in several themes. Filter only icon assets.
  const rows=await query(`WITH icons AS (
   SELECT DISTINCT ON (a->>'sha256',a->>'packageName')
    a AS asset,t.id AS "themeId",t.title AS "themeTitle",
    json_build_object('id',u.id,'username',u.username,'displayName',u.display_name,'avatarUrl',u.avatar_url) AS author,
    COALESCE(a->>'category','Sistema') AS category,COALESCE(a->>'style','Colorido') AS style,t.created_at
   FROM themes t JOIN users u ON u.id=t.author_id
   CROSS JOIN LATERAL jsonb_array_elements(t.manifest->'assets') a
   WHERE a->>'kind'='icon' AND a->>'packageName' IS NOT NULL
   ORDER BY a->>'sha256',a->>'packageName',t.created_at DESC,t.id DESC
  ) SELECT asset,"themeId","themeTitle",author,category,style FROM icons
  WHERE ($1::text IS NULL OR category=$1) AND ($2::text IS NULL OR style=$2)
  AND ($3::text IS NULL OR (asset->>'name') ILIKE $3)
  ORDER BY created_at DESC,asset->>'id' LIMIT $4 OFFSET $5`,
  [q.category??null,q.style??null,q.search?'%'+q.search.replace(/[%_\\]/g,'\\$&')+'%':null,q.limit+1,q.offset]);
  return {items:rows.slice(0,q.limit),nextOffset:rows.length>q.limit?q.offset+q.limit:null};
 });
}
