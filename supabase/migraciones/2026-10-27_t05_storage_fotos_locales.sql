-- T-05 · Supabase Storage: fotos de los locales. Ejecutar UNA vez en Supabase → SQL Editor,
-- después de las migraciones de T-04. Se puede volver a ejecutar.
--
-- • Bucket público `locales`: cualquiera puede ver las fotos por su URL pública (las tarjetas del
--   Inicio, Explorar y el detalle las cargan con Coil).
-- • Cada foto se guarda en la carpeta de su local: `<local_id>/portada-<marca de tiempo>.jpg`.
-- • Solo el oferente dueño del local puede subir o borrar fotos en esa carpeta.
-- • La app guarda la URL pública en `locales.imagen_url`.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('locales', 'locales', true, 5242880, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update
  set public = true,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

-- ¿La carpeta de este archivo es un local de la cuenta actual?
create or replace function public.es_carpeta_de_mi_local(nombre text)
returns boolean language sql stable security definer set search_path = '' as $$
  select public.es_oferente() and exists (
    select 1 from public.locales l
    where l.id::text = (storage.foldername(nombre))[1] and l.oferente_id = auth.uid()
  );
$$;

drop policy if exists "fotos locales: el oferente ve las suyas" on storage.objects;
create policy "fotos locales: el oferente ve las suyas" on storage.objects for select to authenticated
  using (bucket_id = 'locales' and public.es_carpeta_de_mi_local(name));

drop policy if exists "fotos locales: el oferente sube" on storage.objects;
create policy "fotos locales: el oferente sube" on storage.objects for insert to authenticated
  with check (bucket_id = 'locales' and public.es_carpeta_de_mi_local(name));

drop policy if exists "fotos locales: el oferente borra" on storage.objects;
create policy "fotos locales: el oferente borra" on storage.objects for delete to authenticated
  using (bucket_id = 'locales' and public.es_carpeta_de_mi_local(name));

-- El oferente también puede cambiar la foto de su local (las políticas de T-04 limitan a sus filas).
grant update (imagen_url) on public.locales to authenticated;

-- Verificación: el bucket y sus políticas.
select id, public, file_size_limit from storage.buckets where id = 'locales';
select policyname, cmd from pg_policies where schemaname = 'storage' and tablename = 'objects' and policyname like 'fotos locales%';
