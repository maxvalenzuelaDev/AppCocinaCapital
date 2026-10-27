-- T-05 · Fotos de los locales de prueba (los que no tienen oferente y no se pueden editar desde la app).
-- Requiere la migración 2026-10-27_t05_storage_fotos_locales.sql.
--
-- 1. Ver el id de cada local:
select id, nombre, imagen_url from public.locales order by nombre;

-- 2. En Storage → bucket `locales`, crear una carpeta por local con su id como nombre y subir dentro
--    una foto ficticia o libre de derechos (JPG o PNG, hasta 5 MB). Por ejemplo: <id>/portada.jpg
--
-- 3. Ejecutar esto: guarda en cada local la URL pública de la foto más reciente de su carpeta.
update public.locales l
set imagen_url = 'https://pwuwpypnjbgdqbrwslrp.supabase.co/storage/v1/object/public/locales/' || f.name
from (
  select distinct on (split_part(name, '/', 1)) split_part(name, '/', 1) as local_id, name
  from storage.objects
  where bucket_id = 'locales'
  order by split_part(name, '/', 1), created_at desc
) f
where l.id::text = f.local_id;

-- Verificación: cada local con su foto.
select nombre, imagen_url from public.locales order by nombre;
