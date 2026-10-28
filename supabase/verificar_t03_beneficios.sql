-- Prueba de las funciones de la billetera (T-03). No deja datos: todo ocurre dentro de un bloque que
-- termina con un error a propósito, y Postgres revierte lo hecho. El resultado viene en ese mensaje.
-- Necesita un oferente con local, otro oferente y dos consumidores (los primeros que encuentre).
do $$
declare
  ofe uuid := (select l.oferente_id from public.locales l join public.perfiles p on p.id = l.oferente_id
               where p.rol = 'oferente' limit 1);
  ofe2 uuid := (select id from public.perfiles where rol = 'oferente' and id <> ofe limit 1);
  a uuid := (select id from public.perfiles where rol = 'consumidor' order by id limit 1);
  b uuid := (select id from public.perfiles where rol = 'consumidor' and id <> a order by id limit 1);
  loc uuid; promo uuid; vencida uuid; horario uuid;
  ben public.beneficios_usuario; ben2 public.beneficios_usuario;
  r record; log text := '';
begin
  select id into loc from public.locales where oferente_id = ofe limit 1;
  insert into public.promociones (local_id, titulo, descuento_porcentaje, vigente_desde, vigente_hasta, cupos_total)
    values (loc, 'PRUEBA', 10, now() - interval '1 day', now() + interval '1 day', 1) returning id into promo;
  insert into public.promociones (local_id, titulo, descuento_porcentaje, vigente_desde, vigente_hasta)
    values (loc, 'PRUEBA VENCIDA', 10, now() - interval '2 day', now() - interval '1 day') returning id into vencida;
  -- Solo aplica mañana: hoy queda fuera de horario.
  insert into public.promociones (local_id, titulo, descuento_porcentaje, vigente_desde, vigente_hasta, dias_semana)
    values (loc, 'PRUEBA HORARIO', 10, now() - interval '1 day', now() + interval '1 day',
            array[((extract(isodow from now() at time zone 'America/Santiago')::int % 7) + 1)]::smallint[])
    returning id into horario;

  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  ben := public.adquirir_beneficio(promo);
  log := log || format('A adquiere: codigo=%s cupos=%s | ', ben.codigo, (select cupos_disponibles from public.promociones where id = promo));
  ben2 := public.adquirir_beneficio(promo);
  log := log || format('A repite: mismo=%s cupos=%s | ', ben2.codigo = ben.codigo, (select cupos_disponibles from public.promociones where id = promo));
  begin perform public.adquirir_beneficio(vencida); exception when others then log := log || 'vencida: ' || sqlerrm || ' | '; end;
  ben2 := public.adquirir_beneficio(horario);

  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  begin perform public.adquirir_beneficio(promo); exception when others then log := log || 'B sin cupo: ' || sqlerrm || ' | '; end;
  begin perform * from public.consultar_beneficio(ben.codigo); exception when others then log := log || 'consumidor consulta: ' || sqlerrm || ' | '; end;
  begin perform public.liberar_beneficio(ben.id); exception when others then log := log || 'B libera ajeno: ' || sqlerrm || ' | '; end;

  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  perform public.liberar_beneficio(ben.id);
  log := log || format('A libera: cupos=%s | ', (select cupos_disponibles from public.promociones where id = promo));
  ben := public.adquirir_beneficio(promo);

  perform set_config('request.jwt.claims', json_build_object('sub', ofe2, 'role', 'authenticated')::text, true);
  begin perform * from public.consultar_beneficio(ben.codigo); exception when others then log := log || 'otro oferente: ' || sqlerrm || ' | '; end;

  perform set_config('request.jwt.claims', json_build_object('sub', ofe, 'role', 'authenticated')::text, true);
  select * into r from public.consultar_beneficio(lower(substr(ben.codigo, 1, 4)) || '-' || substr(ben.codigo, 5));
  log := log || format('consulta: %s | ', r.estado);
  select * into r from public.canjear_beneficio(ben.codigo);
  log := log || format('canje: %s ahora=%s | ', r.estado, r.canjeado_ahora);
  select * into r from public.canjear_beneficio(ben.codigo);
  log := log || format('recanje: %s ahora=%s | ', r.estado, r.canjeado_ahora);
  select * into r from public.canjear_beneficio(ben2.codigo);
  log := log || format('fuera de horario: %s ahora=%s | ', r.estado, r.canjeado_ahora);
  update public.promociones set vigente_hasta = now() - interval '1 minute' where id = horario;
  select * into r from public.consultar_beneficio(ben2.codigo);
  log := log || format('vencido: %s | ', r.estado);

  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  begin perform public.liberar_beneficio(ben.id); exception when others then log := log || 'libera canjeado: ' || sqlerrm || ' | '; end;
  log := log || 'mis_beneficios: ' || (select string_agg(titulo || '=' || estado, ', ') from public.mis_beneficios() where titulo like 'PRUEBA%');

  raise exception 'RESULTADO (se revierte): %', log;
end $$;

-- Resultado esperado (2026-10-28):
-- A adquiere: cupos=0 | A repite: mismo=t cupos=0 | vencida: PROMOCION_VENCIDA | B sin cupo: SIN_CUPOS |
-- consumidor consulta: NO_AUTORIZADO | B libera ajeno: BENEFICIO_NO_ENCONTRADO | A libera: cupos=1 |
-- otro oferente: CODIGO_NO_ENCONTRADO | consulta: valido | canje: canjeado ahora=t | recanje: canjeado ahora=f |
-- fuera de horario: fuera_de_horario ahora=f | vencido: vencido | libera canjeado: BENEFICIO_CANJEADO |
-- mis_beneficios: PRUEBA HORARIO=vencido, PRUEBA=canjeado
