-- HU-09 / RF-07: listado y detalle de propuestas culinarias.
-- Ejecutar en el SQL Editor del proyecto. No carga experiencias ni precios inventados.
begin;

create table if not exists public.experiencias (
  id uuid primary key default gen_random_uuid(),
  local_id uuid not null references public.locales(id) on delete cascade,
  titulo text not null check (length(btrim(titulo)) > 0),
  descripcion text not null default '',
  horario text,
  precio_referencial bigint check (precio_referencial >= 0),
  imagen_url text,
  activa boolean not null default true
);

comment on column public.experiencias.precio_referencial is
  'Precio orientativo de la experiencia en CLP; NULL si no está informado. No equivale al consumo promedio del local.';
comment on column public.experiencias.horario is
  'Horario propio de la experiencia; no se deduce del horario general del comercio.';

create index if not exists experiencias_local_idx on public.experiencias(local_id);
create index if not exists experiencias_publicadas_titulo_idx on public.experiencias(titulo, id) where activa;

alter table public.experiencias enable row level security;
revoke all on public.experiencias from anon, authenticated;
grant select on public.experiencias to anon, authenticated;

drop policy if exists "lectura experiencias publicadas" on public.experiencias;
create policy "lectura experiencias publicadas" on public.experiencias
  for select to anon, authenticated
  using (activa and exists (
    select 1 from public.locales l where l.id = experiencias.local_id and l.activo
  ));

-- HU-09 es de consulta: no habilita escritura desde la app del consumidor.
commit;
