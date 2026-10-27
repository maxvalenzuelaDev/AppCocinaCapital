-- Ejecutar después de la migración y experiencia_de_prueba.sql.
-- Las filas auxiliares se revierten al finalizar; la publicación de prueba se conserva.
begin;

do $$
begin
  if not (select relrowsecurity from pg_class where oid = 'public.experiencias'::regclass) then
    raise exception 'HU-09: RLS no está habilitado';
  end if;
  if not exists (select 1 from public.experiencias where id = 'dcf9f042-a832-4d2f-9025-e7790385517e' and activa) then
    raise exception 'HU-09: falta la publicación de prueba';
  end if;
end $$;

insert into public.locales (id, nombre, categoria_id, comuna, activo)
select '8811af58-14ef-488a-8214-91c6e4ae0011', 'Local auxiliar HU-09', categoria_id, comuna, false
from public.locales where id = '5c71deb5-5861-411b-8773-d1fab6de1609';

insert into public.experiencias (id, local_id, titulo, activa) values
  ('6cf39dfb-a079-4a33-ab0b-899c768bdd01', '5c71deb5-5861-411b-8773-d1fab6de1609', 'Auxiliar inactiva HU-09', false),
  ('6cf39dfb-a079-4a33-ab0b-899c768bdd02', '8811af58-14ef-488a-8214-91c6e4ae0011', 'Auxiliar local inactivo HU-09', true);

set local role anon;
do $$
begin
  if not exists (select 1 from public.experiencias where id = 'dcf9f042-a832-4d2f-9025-e7790385517e') then
    raise exception 'HU-09: anon no puede consultar la experiencia activa';
  end if;
  if exists (select 1 from public.experiencias where id in ('6cf39dfb-a079-4a33-ab0b-899c768bdd01', '6cf39dfb-a079-4a33-ab0b-899c768bdd02')) then
    raise exception 'HU-09: anon puede consultar contenido inactivo';
  end if;
  if has_table_privilege(current_user, 'public.experiencias', 'INSERT')
    or has_table_privilege(current_user, 'public.experiencias', 'UPDATE')
    or has_table_privilege(current_user, 'public.experiencias', 'DELETE') then
    raise exception 'HU-09: anon tiene permisos de escritura';
  end if;
end $$;
reset role;

set local role authenticated;
do $$
begin
  if not exists (select 1 from public.experiencias where id = 'dcf9f042-a832-4d2f-9025-e7790385517e') then
    raise exception 'HU-09: authenticated no puede consultar la experiencia activa';
  end if;
  if exists (select 1 from public.experiencias where id in ('6cf39dfb-a079-4a33-ab0b-899c768bdd01', '6cf39dfb-a079-4a33-ab0b-899c768bdd02')) then
    raise exception 'HU-09: authenticated puede consultar contenido inactivo';
  end if;
  if has_table_privilege(current_user, 'public.experiencias', 'INSERT')
    or has_table_privilege(current_user, 'public.experiencias', 'UPDATE')
    or has_table_privilege(current_user, 'public.experiencias', 'DELETE') then
    raise exception 'HU-09: authenticated tiene permisos de escritura';
  end if;
end $$;
reset role;

select 'HU-09: lectura y permisos correctos para anon y authenticated' as resultado;
rollback;
