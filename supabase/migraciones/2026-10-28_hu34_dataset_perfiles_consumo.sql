-- HU-34 · Dataset ficticio y anonimizado de perfiles de consumo. Ejecutar en Supabase → SQL Editor.
-- Se puede volver a ejecutar. Los datos se cargan con supabase/dataset_perfiles_consumo.sql.
--
-- No son cuentas: no hay correos, nombres ni usuarios de Auth. Cada perfil es un alias ("P-001") con
-- rasgos de consumo agregados (rango de edad, comuna, gustos y presupuesto) y un historial simulado de
-- interacciones sobre el catálogo de prueba. Sirve para probar las recomendaciones (HU-21, HU-24) y
-- las métricas del oferente (HU-30) sin usar datos personales reales (RA-01, RNF-05).

create table if not exists public.dataset_perfiles_consumo (
  id text primary key,                                   -- alias, ej: P-001
  comuna text not null,
  rango_edad text not null,
  categorias_preferidas text[] not null,                 -- nombres de `categorias`
  rango_precio text not null,
  presupuesto_persona int not null check (presupuesto_persona > 0), -- CLP
  dias_preferidos smallint[] not null,                   -- 1 = lunes … 7 = domingo
  franja_preferida text not null,
  visitas_mes smallint not null check (visitas_mes between 0 and 31),
  constraint dataset_perfiles_rango_edad check (rango_edad in ('18-24', '25-34', '35-44', '45-54', '55+')),
  constraint dataset_perfiles_rango_precio check (rango_precio in ('ECONOMICO', 'MEDIO', 'PREMIUM')),
  constraint dataset_perfiles_franja check (franja_preferida in ('desayuno', 'almuerzo', 'tarde', 'cena')),
  constraint dataset_perfiles_dias check (dias_preferidos <@ array[1, 2, 3, 4, 5, 6, 7]::smallint[])
);

create table if not exists public.dataset_interacciones (
  id bigint generated always as identity primary key,
  perfil_id text not null references public.dataset_perfiles_consumo(id) on delete cascade,
  tipo text not null,
  local_id uuid references public.locales(id) on delete cascade,
  promocion_id uuid references public.promociones(id) on delete set null,
  termino text,                                          -- solo en búsquedas
  ocurrido_en timestamptz not null,
  constraint dataset_interacciones_tipo check (tipo in ('busqueda', 'vista_local', 'vista_promocion', 'canje'))
);
create index if not exists dataset_interacciones_perfil on public.dataset_interacciones (perfil_id);
create index if not exists dataset_interacciones_local on public.dataset_interacciones (local_id);
create index if not exists dataset_interacciones_promocion on public.dataset_interacciones (promocion_id);

-- Solo lectura para la app con sesión iniciada; se cargan desde el SQL Editor.
alter table public.dataset_perfiles_consumo enable row level security;
alter table public.dataset_interacciones enable row level security;
drop policy if exists "lectura dataset perfiles" on public.dataset_perfiles_consumo;
create policy "lectura dataset perfiles" on public.dataset_perfiles_consumo for select to authenticated using (true);
drop policy if exists "lectura dataset interacciones" on public.dataset_interacciones;
create policy "lectura dataset interacciones" on public.dataset_interacciones for select to authenticated using (true);

revoke all on public.dataset_perfiles_consumo from anon, authenticated;
revoke all on public.dataset_interacciones from anon, authenticated;
grant select on public.dataset_perfiles_consumo to authenticated;
grant select on public.dataset_interacciones to authenticated;
