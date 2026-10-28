-- HU-30 · Métricas del oferente. Ejecutar después de 2026-10-28_hu20_t07_interacciones_y_cuenta.sql.
-- Se puede volver a ejecutar.
--
-- Contadores por promoción de un local del oferente que llama: vistas del detalle, beneficios
-- obtenidos y canjeados. El oferente no puede leer las filas de los consumidores (RLS); esta función
-- solo entrega totales, sin datos de personas. Las vistas cuentan solo a quienes dieron su
-- consentimiento (HU-20); los beneficios cuentan todos, porque son parte del servicio.

create or replace function public.metricas_promociones(p_local_id uuid)
returns table (promocion_id uuid, vistas integer, obtenidos integer, canjeados integer)
language plpgsql stable security definer set search_path = '' as $$
begin
  if not public.es_oferente()
     or not exists (select 1 from public.locales where id = p_local_id and oferente_id = auth.uid()) then
    raise exception 'NO_AUTORIZADO' using errcode = '42501';
  end if;
  return query
    select p.id,
           (select count(*)::int from public.interacciones i
             where i.promocion_id = p.id and i.tipo = 'vista_promocion'),
           (select count(*)::int from public.beneficios_usuario b where b.promocion_id = p.id),
           (select count(*)::int from public.beneficios_usuario b where b.promocion_id = p.id and b.estado = 'canjeado')
    from public.promociones p
    where p.local_id = p_local_id;
end;
$$;

revoke execute on function public.metricas_promociones(uuid) from public, anon;
grant execute on function public.metricas_promociones(uuid) to authenticated;
