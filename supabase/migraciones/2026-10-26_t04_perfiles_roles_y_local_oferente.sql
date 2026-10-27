-- T-04 · Roles consumidor/oferente y políticas por rol (base de HU-04 y HU-25 a HU-30).
-- Ejecutar UNA vez en Supabase → SQL Editor, después de las migraciones anteriores.
-- Se puede volver a ejecutar sin duplicar nada.
--
-- Resumen:
--   • perfiles(id, nombre, rol): una fila por cuenta, creada sola al registrarse. Toda cuenta nace consumidora.
--   • El rol se cambia solo desde el SQL Editor (ver supabase/asignar_oferente.sql); la app no puede modificarlo.
--   • Cada cambio de rol se copia a app_metadata, que es lo que lee la app al iniciar sesión (HU-04).
--   • locales.oferente_id enlaza cada local con su oferente (un local por oferente).
--   • El oferente solo puede crear y editar SU local, sus platos y sus promociones; el consumidor solo lee.

-- ---------- Perfiles ----------
create table if not exists public.perfiles (
  id uuid primary key references auth.users(id) on delete cascade,
  nombre text not null default '',
  rol text not null default 'consumidor' check (rol in ('consumidor', 'oferente')),
  creado_en timestamptz not null default now()
);

alter table public.perfiles enable row level security;
drop policy if exists "lectura perfil propio" on public.perfiles;
create policy "lectura perfil propio" on public.perfiles for select to authenticated using (id = auth.uid());
-- Sin políticas de insert/update/delete: desde la app nadie puede crear perfiles ni cambiar su rol.

-- Copia el rol a app_metadata (el token de sesión lo incluye al volver a iniciar sesión).
create or replace function public.sincronizar_rol()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  update auth.users
  set raw_app_meta_data = coalesce(raw_app_meta_data, '{}'::jsonb) || jsonb_build_object('rol', new.rol)
  where id = new.id;
  return new;
end;
$$;

drop trigger if exists perfiles_sincronizar_rol on public.perfiles;
create trigger perfiles_sincronizar_rol
  after insert or update of rol on public.perfiles
  for each row execute function public.sincronizar_rol();

-- Crea el perfil de cada cuenta nueva (registro desde la app o "Add user" en el panel).
create or replace function public.crear_perfil()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.perfiles (id, nombre, rol)
  values (
    new.id,
    coalesce(new.raw_user_meta_data ->> 'nombre', new.raw_user_meta_data ->> 'full_name', ''),
    case when new.raw_app_meta_data ->> 'rol' = 'oferente' then 'oferente' else 'consumidor' end
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists al_crear_usuario on auth.users;
create trigger al_crear_usuario
  after insert on auth.users
  for each row execute function public.crear_perfil();

-- Perfiles de las cuentas que ya existían (conserva el rol oferente si se asignó antes en app_metadata).
insert into public.perfiles (id, nombre, rol)
select id,
       coalesce(raw_user_meta_data ->> 'nombre', raw_user_meta_data ->> 'full_name', ''),
       case when raw_app_meta_data ->> 'rol' = 'oferente' then 'oferente' else 'consumidor' end
from auth.users
on conflict (id) do nothing;

-- Se usa en las políticas: lee el rol vigente en la tabla, no el que quedó guardado en el token.
create or replace function public.es_oferente()
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.perfiles where id = auth.uid() and rol = 'oferente');
$$;

-- ---------- Local del oferente ----------
alter table public.locales add column if not exists oferente_id uuid references auth.users(id) on delete set null;
create unique index if not exists locales_un_local_por_oferente on public.locales (oferente_id) where oferente_id is not null;

-- El oferente ve su local aunque esté inactivo; los consumidores siguen viendo solo los activos.
drop policy if exists "oferente lee su local" on public.locales;
create policy "oferente lee su local" on public.locales for select to authenticated
  using (oferente_id = auth.uid());

drop policy if exists "oferente crea su local" on public.locales;
create policy "oferente crea su local" on public.locales for insert to authenticated
  with check (oferente_id = auth.uid() and public.es_oferente());

drop policy if exists "oferente edita su local" on public.locales;
create policy "oferente edita su local" on public.locales for update to authenticated
  using (oferente_id = auth.uid() and public.es_oferente())
  with check (oferente_id = auth.uid());

-- El oferente solo escribe los datos del perfil del local (HU-25). La valoración, el estado activo
-- y la imagen quedan fuera de su alcance.
revoke insert, update on public.locales from authenticated;
grant insert (oferente_id, nombre, categoria_id, comuna, descripcion, direccion, horario, ticket_promedio_persona, latitud, longitud)
  on public.locales to authenticated;
grant update (nombre, categoria_id, comuna, descripcion, direccion, horario, ticket_promedio_persona, latitud, longitud)
  on public.locales to authenticated;

-- ---------- Catálogo y promociones del oferente (HU-26 a HU-28) ----------
drop policy if exists "oferente gestiona sus platos" on public.platos;
create policy "oferente gestiona sus platos" on public.platos for all to authenticated
  using (exists (select 1 from public.locales l where l.id = local_id and l.oferente_id = auth.uid()) and public.es_oferente())
  with check (exists (select 1 from public.locales l where l.id = local_id and l.oferente_id = auth.uid()) and public.es_oferente());

drop policy if exists "oferente gestiona sus promociones" on public.promociones;
create policy "oferente gestiona sus promociones" on public.promociones for all to authenticated
  using (exists (select 1 from public.locales l where l.id = local_id and l.oferente_id = auth.uid()) and public.es_oferente())
  with check (exists (select 1 from public.locales l where l.id = local_id and l.oferente_id = auth.uid()) and public.es_oferente());

-- Verificación: cada cuenta con su perfil y su rol.
select u.email, p.rol, u.raw_app_meta_data ->> 'rol' as rol_en_sesion
from auth.users u left join public.perfiles p on p.id = u.id
order by u.email;
