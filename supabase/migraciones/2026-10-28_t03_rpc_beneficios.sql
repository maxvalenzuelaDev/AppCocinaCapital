-- T-03 · Funciones de la billetera: adquirir, liberar, listar y canjear beneficios (HU-13 a HU-16 y
-- HU-29). Ejecutar después de 2026-10-28_t02_beneficios_usuario.sql. Se puede volver a ejecutar.
--
-- Son `security definer` porque la app ya no escribe `beneficios_usuario` ni los cupos de
-- `promociones`: cada función valida quién llama (auth.uid()) y hace todo en una sola transacción.
-- Los errores se lanzan con un código en mayúsculas que la app traduce a un mensaje:
--   SIN_SESION, PROMOCION_NO_DISPONIBLE, PROMOCION_NO_INICIADA, PROMOCION_VENCIDA, SIN_CUPOS,
--   BENEFICIO_NO_ENCONTRADO, BENEFICIO_CANJEADO, NO_AUTORIZADO, CODIGO_NO_ENCONTRADO.

-- Estado de un beneficio al momento de canjearlo en el local. El horario se evalúa en Santiago.
--   canjeado · vencido · no_disponible (pausada o local inactivo) · no_iniciado · fuera_de_horario · valido
create or replace function public.estado_canje_beneficio(p_estado text, p public.promociones, p_local_activo boolean)
returns text language sql stable set search_path = '' as $$
  select case
    when p_estado = 'canjeado' then 'canjeado'
    when now() >= p.vigente_hasta then 'vencido'
    when not p.activa or not coalesce(p_local_activo, false) then 'no_disponible'
    when now() < p.vigente_desde then 'no_iniciado'
    when p.dias_semana is not null
      and not (extract(isodow from now() at time zone 'America/Santiago')::smallint = any (p.dias_semana))
      then 'fuera_de_horario'
    when p.hora_inicio is not null
      and not ((now() at time zone 'America/Santiago')::time >= p.hora_inicio
           and (now() at time zone 'America/Santiago')::time < p.hora_fin)
      then 'fuera_de_horario'
    else 'valido'
  end;
$$;

-- HU-13 · El consumidor obtiene el beneficio: valida vigencia y cupo y descuenta el cupo.
-- Bloquea la fila de la promoción, así dos personas no pueden tomar el último cupo a la vez.
-- Si ya lo tenía, devuelve el mismo beneficio sin descontar otro cupo.
create or replace function public.adquirir_beneficio(p_promocion_id uuid)
returns public.beneficios_usuario
language plpgsql security definer set search_path = '' as $$
declare
  v_usuario uuid := auth.uid();
  v_promo public.promociones;
  v_local_activo boolean;
  v_beneficio public.beneficios_usuario;
begin
  if v_usuario is null then
    raise exception 'SIN_SESION' using errcode = '28000';
  end if;

  select * into v_promo from public.promociones where id = p_promocion_id for update;
  if not found then
    raise exception 'PROMOCION_NO_DISPONIBLE';
  end if;

  select * into v_beneficio from public.beneficios_usuario
  where usuario_id = v_usuario and promocion_id = p_promocion_id;
  if found then
    return v_beneficio;
  end if;

  select activo into v_local_activo from public.locales where id = v_promo.local_id;
  if not v_promo.activa or not coalesce(v_local_activo, false) then
    raise exception 'PROMOCION_NO_DISPONIBLE';
  elsif now() < v_promo.vigente_desde then
    raise exception 'PROMOCION_NO_INICIADA';
  elsif now() >= v_promo.vigente_hasta then
    raise exception 'PROMOCION_VENCIDA';
  end if;

  if v_promo.cupos_disponibles is not null then
    if v_promo.cupos_disponibles <= 0 then
      raise exception 'SIN_CUPOS';
    end if;
    update public.promociones set cupos_disponibles = cupos_disponibles - 1 where id = p_promocion_id;
  end if;

  -- El par usuario-promoción ya se revisó con la promoción bloqueada: solo puede repetirse el código.
  for intento in 1..5 loop
    begin
      insert into public.beneficios_usuario (usuario_id, promocion_id)
      values (v_usuario, p_promocion_id)
      returning * into v_beneficio;
      return v_beneficio;
    exception when unique_violation then
      null;
    end;
  end loop;
  raise exception 'No se pudo generar un código único';
end;
$$;

-- El consumidor quita un beneficio que aún no usa: se borra y devuelve el cupo a la promoción si
-- sigue vigente. Los canjeados no se pueden quitar: forman su historial (HU-16).
create or replace function public.liberar_beneficio(p_beneficio_id uuid)
returns void
language plpgsql security definer set search_path = '' as $$
declare
  v_promocion uuid;
begin
  select promocion_id into v_promocion from public.beneficios_usuario
  where id = p_beneficio_id and usuario_id = auth.uid();
  if not found then
    raise exception 'BENEFICIO_NO_ENCONTRADO';
  end if;

  -- Mismo orden de bloqueo que adquirir_beneficio (primero la promoción) para no trabarse entre sí.
  perform 1 from public.promociones where id = v_promocion for update;

  delete from public.beneficios_usuario
  where id = p_beneficio_id and usuario_id = auth.uid() and estado = 'vigente';
  if not found then
    raise exception 'BENEFICIO_CANJEADO';
  end if;

  update public.promociones
  set cupos_disponibles = least(cupos_disponibles + 1, cupos_total)
  where id = v_promocion and cupos_disponibles is not null and vigente_hasta > now();
end;
$$;

-- HU-14 y HU-16 · Billetera del consumidor con los datos de cada promoción y su local, en una sola
-- consulta. Incluye las promociones pausadas o vencidas, que la política pública ya no muestra.
-- estado: vigente · canjeado · vencido.
create or replace function public.mis_beneficios()
returns table (
  id uuid,
  codigo text,
  estado text,
  guardado_en timestamptz,
  canjeado_en timestamptz,
  promocion_id uuid,
  titulo text,
  descripcion text,
  condiciones text,
  descuento_porcentaje int,
  vigente_hasta timestamptz,
  local_id uuid,
  local_nombre text
)
language sql stable security definer set search_path = '' as $$
  select b.id, b.codigo,
         case when b.estado = 'canjeado' then 'canjeado'
              when now() >= p.vigente_hasta then 'vencido'
              else 'vigente' end,
         b.guardado_en, b.canjeado_en, p.id, p.titulo, p.descripcion, p.condiciones,
         p.descuento_porcentaje, p.vigente_hasta, l.id, l.nombre
  from public.beneficios_usuario b
  join public.promociones p on p.id = b.promocion_id
  join public.locales l on l.id = p.local_id
  where b.usuario_id = auth.uid()
  order by b.guardado_en desc;
$$;

-- HU-29 · El oferente revisa un código antes de canjearlo. Solo encuentra los beneficios de
-- promociones de sus locales; un código de otro local responde igual que uno inexistente.
-- Acepta el código con o sin guion y en minúsculas (lo que se escribe a mano o trae el QR).
create or replace function public.consultar_beneficio(p_codigo text)
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
language plpgsql stable security definer set search_path = '' as $$
#variable_conflict use_column
declare
  v_codigo text := upper(regexp_replace(coalesce(p_codigo, ''), '[^A-Za-z0-9]', '', 'g'));
begin
  if not public.es_oferente() then
    raise exception 'NO_AUTORIZADO' using errcode = '42501';
  end if;
  return query
    select b.codigo, public.estado_canje_beneficio(b.estado, p, l.activo), b.canjeado_en, p.titulo,
           p.descuento_porcentaje, p.condiciones, p.vigente_hasta, l.nombre, false
    from public.beneficios_usuario b
    join public.promociones p on p.id = b.promocion_id
    join public.locales l on l.id = p.local_id
    where b.codigo = v_codigo and l.oferente_id = auth.uid();
  if not found then
    raise exception 'CODIGO_NO_ENCONTRADO';
  end if;
end;
$$;

-- HU-15 y HU-29 · El oferente canjea el código: si está válido lo marca canjeado con la fecha.
-- Si no, no cambia nada y devuelve su estado (ya canjeado, vencido, fuera de horario…).
-- `canjeado_ahora` distingue un canje recién hecho de uno anterior.
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
begin
  if not public.es_oferente() then
    raise exception 'NO_AUTORIZADO' using errcode = '42501';
  end if;

  -- Bloquea el beneficio: dos canjes simultáneos del mismo código no pueden pasar los dos.
  select b.id, public.estado_canje_beneficio(b.estado, p, l.activo) into v_id, v_estado
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

-- Permisos: solo con sesión iniciada. La función de estado es interna (la usan las de canje).
revoke execute on function public.estado_canje_beneficio(text, public.promociones, boolean) from public, anon, authenticated;
revoke execute on function public.adquirir_beneficio(uuid) from public, anon;
revoke execute on function public.liberar_beneficio(uuid) from public, anon;
revoke execute on function public.mis_beneficios() from public, anon;
revoke execute on function public.consultar_beneficio(text) from public, anon;
revoke execute on function public.canjear_beneficio(text) from public, anon;
grant execute on function public.adquirir_beneficio(uuid) to authenticated;
grant execute on function public.liberar_beneficio(uuid) to authenticated;
grant execute on function public.mis_beneficios() to authenticated;
grant execute on function public.consultar_beneficio(text) to authenticated;
grant execute on function public.canjear_beneficio(text) to authenticated;
