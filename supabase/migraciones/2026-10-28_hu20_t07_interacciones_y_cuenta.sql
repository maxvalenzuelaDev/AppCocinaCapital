-- HU-20 · Registro de interacciones y T-07 · Borrar historial y eliminar cuenta.
-- Ejecutar después de 2026-10-28_t06_preferencias_y_consentimientos.sql. Se puede volver a ejecutar.
--
-- `interacciones` guarda búsquedas, vistas de locales y promociones y canjes para recomendar ofertas
-- (RF-13). Solo se aceptan si la cuenta tiene el consentimiento activo en `preferencias_usuario`
-- (HU-03): la regla está en Supabase, no solo en la app. Revocar el consentimiento detiene el
-- registro; lo ya guardado se borra con borrar_historial() o al eliminar la cuenta.

create table if not exists public.interacciones (
  id bigint generated always as identity primary key,
  usuario_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  tipo text not null,
  local_id uuid references public.locales(id) on delete set null,
  promocion_id uuid references public.promociones(id) on delete set null,
  termino text,
  ocurrido_en timestamptz not null default now(),
  constraint interacciones_tipo check (tipo in ('busqueda', 'vista_local', 'vista_promocion', 'canje')),
  constraint interacciones_termino check (termino is null or char_length(termino) <= 100)
);
create index if not exists interacciones_usuario on public.interacciones (usuario_id, ocurrido_en desc);
create index if not exists interacciones_local on public.interacciones (local_id);
create index if not exists interacciones_promocion on public.interacciones (promocion_id);

-- Solo responde por quien llama: no sirve para consultar el consentimiento de otra cuenta.
create or replace function public.tiene_consentimiento()
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.preferencias_usuario where usuario_id = auth.uid() and consentimiento);
$$;

alter table public.interacciones enable row level security;
drop policy if exists "ver mis interacciones" on public.interacciones;
create policy "ver mis interacciones" on public.interacciones
  for select to authenticated using (usuario_id = auth.uid());
-- Los canjes los registra canjear_beneficio; la app solo envía búsquedas y vistas.
drop policy if exists "registrar mis interacciones con consentimiento" on public.interacciones;
create policy "registrar mis interacciones con consentimiento" on public.interacciones
  for insert to authenticated
  with check (usuario_id = auth.uid() and tipo <> 'canje' and public.tiene_consentimiento());
drop policy if exists "borrar mis interacciones" on public.interacciones;
create policy "borrar mis interacciones" on public.interacciones
  for delete to authenticated using (usuario_id = auth.uid());

revoke all on public.interacciones from anon, authenticated;
grant select, delete on public.interacciones to authenticated;
grant insert (tipo, local_id, promocion_id, termino) on public.interacciones to authenticated;

-- T-07 · Borra el historial de quien llama (RLS limita las filas). Devuelve cuántas se borraron.
create or replace function public.borrar_historial()
returns integer language plpgsql security invoker set search_path = '' as $$
declare
  v_borradas integer;
begin
  delete from public.interacciones where usuario_id = auth.uid();
  get diagnostics v_borradas = row_count;
  return v_borradas;
end;
$$;

-- T-07 · Elimina la cuenta de quien llama. Al borrar el usuario de Auth se borran en cascada su
-- perfil, preferencias, consentimientos, beneficios e interacciones (Ley 21.719).
create or replace function public.eliminar_cuenta()
returns void language plpgsql security definer set search_path = '' as $$
declare
  v_usuario uuid := auth.uid();
begin
  if v_usuario is null then
    raise exception 'SIN_SESION' using errcode = '28000';
  end if;
  delete from auth.users where id = v_usuario;
end;
$$;

-- HU-20 · El canje en el local también queda como interacción del consumidor, si dio su consentimiento.
-- Igual a la versión de 2026-10-28_t03_rpc_beneficios.sql más ese registro.
create or replace function public.canjear_beneficio(p_codigo text)
returns table (
  codigo text,
  estado text,
  canjeado_en timestamptz,
  titulo text,
  descuento_porcentaje int,
  condiciones text,
  vigente_hasta timestamptz,
  local_nombre text,
  canjeado_ahora boolean
)
language plpgsql security definer set search_path = '' as $$
#variable_conflict use_column
declare
  v_codigo text := upper(regexp_replace(coalesce(p_codigo, ''), '[^A-Za-z0-9]', '', 'g'));
  v_id uuid;
  v_estado text;
  v_usuario uuid;
  v_local uuid;
  v_promocion uuid;
begin
  if not public.es_oferente() then
    raise exception 'NO_AUTORIZADO' using errcode = '42501';
  end if;

  select b.id, public.estado_canje_beneficio(b.estado, p, l.activo), b.usuario_id, l.id, p.id
  into v_id, v_estado, v_usuario, v_local, v_promocion
  from public.beneficios_usuario b
  join public.promociones p on p.id = b.promocion_id
  join public.locales l on l.id = p.local_id
  where b.codigo = v_codigo and l.oferente_id = auth.uid()
  for update of b;
  if not found then
    raise exception 'CODIGO_NO_ENCONTRADO';
  end if;

  if v_estado = 'valido' then
    update public.beneficios_usuario set estado = 'canjeado', canjeado_en = now() where id = v_id;
    if exists (select 1 from public.preferencias_usuario where usuario_id = v_usuario and consentimiento) then
      insert into public.interacciones (usuario_id, tipo, local_id, promocion_id)
      values (v_usuario, 'canje', v_local, v_promocion);
    end if;
  end if;

  return query
    select b.codigo, public.estado_canje_beneficio(b.estado, p, l.activo), b.canjeado_en, p.titulo,
           p.descuento_porcentaje, p.condiciones, p.vigente_hasta, l.nombre, v_estado = 'valido'
    from public.beneficios_usuario b
    join public.promociones p on p.id = b.promocion_id
    join public.locales l on l.id = p.local_id
    where b.id = v_id;
end;
$$;

-- tiene_consentimiento la usa la política de inserción, que se evalúa con el rol de la sesión.
revoke execute on function public.tiene_consentimiento() from public, anon;
grant execute on function public.tiene_consentimiento() to authenticated;
revoke execute on function public.borrar_historial() from public, anon;
revoke execute on function public.eliminar_cuenta() from public, anon;
revoke execute on function public.canjear_beneficio(text) from public, anon;
grant execute on function public.borrar_historial() to authenticated;
grant execute on function public.eliminar_cuenta() to authenticated;
grant execute on function public.canjear_beneficio(text) to authenticated;
