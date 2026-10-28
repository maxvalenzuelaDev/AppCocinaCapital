-- Prueba de interacciones (HU-20), borrar historial y eliminar cuenta (T-07). No deja datos: el
-- bloque termina con un error a propósito y Postgres revierte todo, incluida la cuenta eliminada.
-- Usa el rol `authenticated` para que se apliquen las políticas de RLS.
do $$
declare
  ofe uuid := (select l.oferente_id from public.locales l join public.perfiles p on p.id = l.oferente_id where p.rol = 'oferente' limit 1);
  a uuid := (select id from public.perfiles where rol = 'consumidor' order by id limit 1);
  b uuid := (select id from public.perfiles where rol = 'consumidor' and id <> a order by id limit 1);
  loc uuid; promo uuid; ben public.beneficios_usuario; r record; n int; log text := '';
begin
  select id into loc from public.locales where oferente_id = ofe limit 1;
  insert into public.promociones (local_id, titulo, descuento_porcentaje, vigente_desde, vigente_hasta)
    values (loc, 'PRUEBA', 10, now() - interval '1 day', now() + interval '1 day') returning id into promo;
  -- A acepta el consentimiento; B lo rechaza.
  insert into public.preferencias_usuario (usuario_id, consentimiento, consentimiento_fecha, consentimiento_respondido)
    values (a, true, now(), true) on conflict (usuario_id) do update set consentimiento = true, consentimiento_fecha = now();
  insert into public.preferencias_usuario (usuario_id, consentimiento, consentimiento_respondido)
    values (b, false, true) on conflict (usuario_id) do update set consentimiento = false, consentimiento_fecha = null;

  execute 'set local role authenticated';
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  insert into public.interacciones (tipo, local_id) values ('vista_local', loc);
  log := log || 'A con consentimiento inserta: ok | ';
  begin insert into public.interacciones (tipo) values ('canje'); log := log || 'A canje directo: PERMITIDO (mal) | ';
  exception when others then log := log || 'A canje directo: rechazado | '; end;
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  begin insert into public.interacciones (tipo, termino) values ('busqueda', 'sushi'); log := log || 'B sin consentimiento: PERMITIDO (mal) | ';
  exception when others then log := log || 'B sin consentimiento: rechazado | '; end;
  log := log || format('B ve interacciones de A: %s | ', (select count(*) from public.interacciones where usuario_id = a));
  execute 'reset role';

  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  ben := public.adquirir_beneficio(promo);
  perform set_config('request.jwt.claims', json_build_object('sub', ofe, 'role', 'authenticated')::text, true);
  select * into r from public.canjear_beneficio(ben.codigo);
  log := log || format('canje registrado: %s | ', (select count(*) from public.interacciones where usuario_id = a and tipo = 'canje'));

  execute 'set local role authenticated';
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  n := public.borrar_historial();
  log := log || format('borrar_historial: %s filas, quedan %s | ', n, (select count(*) from public.interacciones where usuario_id = a));
  execute 'reset role';

  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  perform public.eliminar_cuenta();
  log := log || format('eliminar_cuenta: auth=%s perfil=%s prefs=%s beneficios=%s',
    (select count(*) from auth.users where id = a), (select count(*) from public.perfiles where id = a),
    (select count(*) from public.preferencias_usuario where usuario_id = a), (select count(*) from public.beneficios_usuario where usuario_id = a));
  raise exception 'RESULTADO (se revierte): %', log;
end $$;

-- Resultado esperado (2026-10-28):
-- A con consentimiento inserta: ok | A canje directo: rechazado | B sin consentimiento: rechazado |
-- B ve interacciones de A: 0 | canje registrado: 1 | borrar_historial: 2 filas, quedan 0 |
-- eliminar_cuenta: auth=0 perfil=0 prefs=0 beneficios=0
