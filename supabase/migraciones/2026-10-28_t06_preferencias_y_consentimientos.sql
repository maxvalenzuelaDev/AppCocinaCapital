-- T-06 · Preferencias y consentimiento en Supabase (HU-02 y HU-03). Ejecutar en Supabase → SQL Editor.
-- Se puede volver a ejecutar.
--
-- La app guarda las preferencias en Room y las sube aquí; al iniciar sesión en otro teléfono las
-- descarga. `consentimientos` es el registro histórico de cada aceptación o revocación con su fecha
-- (Ley 19.628 / 21.719): solo se agregan filas, nunca se modifican ni borran desde la app.

create table if not exists public.preferencias_usuario (
  usuario_id uuid primary key default auth.uid() references auth.users(id) on delete cascade,
  -- Ids de `categorias` elegidas (selección múltiple).
  categorias uuid[] not null default '{}',
  rango_precio text not null default 'MEDIO',
  notificaciones boolean not null default false,
  solo_cercanas boolean not null default false,
  solo_categorias_favoritas boolean not null default false,
  tema text not null default 'SISTEMA',
  consentimiento boolean not null default false,
  consentimiento_fecha timestamptz,
  consentimiento_respondido boolean not null default false,
  actualizado_en timestamptz not null default now(),
  constraint preferencias_rango_valido check (rango_precio in ('ECONOMICO', 'MEDIO', 'PREMIUM')),
  constraint preferencias_tema_valido check (tema in ('SISTEMA', 'CLARO', 'OSCURO')),
  constraint preferencias_consentimiento_con_fecha check (not consentimiento or consentimiento_fecha is not null)
);

create or replace function public.marcar_actualizado_en()
returns trigger language plpgsql set search_path = '' as $$
begin
  new.actualizado_en := now();
  return new;
end;
$$;

drop trigger if exists preferencias_actualizado_en on public.preferencias_usuario;
create trigger preferencias_actualizado_en
  before update on public.preferencias_usuario
  for each row execute function public.marcar_actualizado_en();

alter table public.preferencias_usuario enable row level security;
drop policy if exists "ver mis preferencias" on public.preferencias_usuario;
create policy "ver mis preferencias" on public.preferencias_usuario
  for select to authenticated using (usuario_id = auth.uid());
drop policy if exists "crear mis preferencias" on public.preferencias_usuario;
create policy "crear mis preferencias" on public.preferencias_usuario
  for insert to authenticated with check (usuario_id = auth.uid());
drop policy if exists "editar mis preferencias" on public.preferencias_usuario;
create policy "editar mis preferencias" on public.preferencias_usuario
  for update to authenticated using (usuario_id = auth.uid()) with check (usuario_id = auth.uid());

revoke all on public.preferencias_usuario from anon, authenticated;
grant select, insert, update on public.preferencias_usuario to authenticated;

create table if not exists public.consentimientos (
  id bigint generated always as identity primary key,
  usuario_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  acepto boolean not null,
  registrado_en timestamptz not null default now()
);
create index if not exists consentimientos_usuario on public.consentimientos (usuario_id, registrado_en desc);

alter table public.consentimientos enable row level security;
drop policy if exists "ver mis consentimientos" on public.consentimientos;
create policy "ver mis consentimientos" on public.consentimientos
  for select to authenticated using (usuario_id = auth.uid());
drop policy if exists "registrar mi consentimiento" on public.consentimientos;
create policy "registrar mi consentimiento" on public.consentimientos
  for insert to authenticated with check (usuario_id = auth.uid());

-- La fecha la pone Supabase: la app solo envía si aceptó o no.
revoke all on public.consentimientos from anon, authenticated;
grant select on public.consentimientos to authenticated;
grant insert (acepto) on public.consentimientos to authenticated;

revoke execute on function public.marcar_actualizado_en() from public, anon, authenticated;
