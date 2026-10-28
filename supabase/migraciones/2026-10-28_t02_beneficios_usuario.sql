-- T-02 · Tabla de beneficios de la billetera (HU-13 a HU-16). Ejecutar en Supabase → SQL Editor,
-- después de 2026-10-27_hu27_promociones_oferente.sql. Se puede volver a ejecutar.
--
-- 1. Versiona `beneficios_usuario` tal como se creó para la billetera (PR #26): un beneficio por
--    consumidor y promoción, con RLS para que cada uno vea solo los suyos.
-- 2. Le agrega el código de canje (se muestra como texto y como QR), el estado y la fecha de canje.
--    "Vencido" no se guarda: se calcula con la vigencia de la promoción.
-- 3. La app ya no escribe la tabla directo: adquirir, liberar y canjear pasan por las funciones de
--    2026-10-28_t03_rpc_beneficios.sql, que validan vigencia y cupos en una sola transacción.

create table if not exists public.beneficios_usuario (
  id uuid primary key default gen_random_uuid(),
  usuario_id uuid not null references auth.users(id) on delete cascade,
  promocion_id uuid not null references public.promociones(id) on delete cascade,
  guardado_en timestamptz not null default now(),
  constraint beneficio_usuario_unico unique (usuario_id, promocion_id)
);

alter table public.beneficios_usuario enable row level security;

drop policy if exists "Ver mis beneficios" on public.beneficios_usuario;
create policy "Ver mis beneficios" on public.beneficios_usuario
  for select to authenticated using (auth.uid() = usuario_id);

-- Código de 8 caracteres sin letras ni números que se confunden (0/O, 1/I/L). Usa bytes de un UUID v4
-- evitando los que fijan la versión y la variante: 31^8 combinaciones (unos 850 mil millones).
create or replace function public.generar_codigo_beneficio()
returns text language sql volatile set search_path = '' as $$
  select string_agg(substr('ABCDEFGHJKMNPQRSTUVWXYZ23456789', (get_byte(r.b, i) % 31) + 1, 1), '' order by i)
  from (select uuid_send(gen_random_uuid()) as b) r,
       unnest(array[0, 1, 2, 3, 4, 5, 10, 11]) as i;
$$;

alter table public.beneficios_usuario
  add column if not exists codigo text default public.generar_codigo_beneficio();
alter table public.beneficios_usuario
  add column if not exists estado text not null default 'vigente';
alter table public.beneficios_usuario
  add column if not exists canjeado_en timestamptz;

-- Si la tabla ya venía con la columna (por ejemplo, creada con schema.sql), `add column if not exists`
-- no le pone el valor por defecto: se fija aparte.
alter table public.beneficios_usuario alter column codigo set default public.generar_codigo_beneficio();

-- Las filas guardadas antes de este cambio también reciben código.
update public.beneficios_usuario set codigo = public.generar_codigo_beneficio() where codigo is null;
alter table public.beneficios_usuario alter column codigo set not null;

create unique index if not exists beneficios_usuario_codigo on public.beneficios_usuario (codigo);
-- Para la llave foránea y para buscar los beneficios de una promoción.
create index if not exists beneficios_usuario_promocion on public.beneficios_usuario (promocion_id);

alter table public.beneficios_usuario drop constraint if exists beneficios_usuario_estado_valido;
alter table public.beneficios_usuario add constraint beneficios_usuario_estado_valido
  check (estado in ('vigente', 'canjeado') and (estado = 'canjeado') = (canjeado_en is not null));

-- Antes la app insertaba y borraba directo; ahora solo puede leer sus filas.
drop policy if exists "Guardar mis beneficios" on public.beneficios_usuario;
drop policy if exists "Eliminar mis beneficios" on public.beneficios_usuario;
revoke all on public.beneficios_usuario from anon, authenticated;
grant select on public.beneficios_usuario to authenticated;

revoke execute on function public.generar_codigo_beneficio() from public, anon, authenticated;

-- Verificación: columnas, políticas y permisos.
select column_name, data_type, column_default from information_schema.columns
where table_schema = 'public' and table_name = 'beneficios_usuario' order by ordinal_position;
select policyname, cmd from pg_policies where tablename = 'beneficios_usuario';
