-- HU-09: publicación de prueba para revisar listado, detalle y acceso al local.
-- Ejecutar después de migraciones/2026-10-27_hu09_experiencias_culinarias.sql.
-- Solo modifica esta experiencia; no cambia el comercio ni sus promociones.
begin;

do $$
declare
  comercio uuid := '5c71deb5-5861-411b-8773-d1fab6de1609';
begin
  if not exists (
    select 1 from public.locales
    where id = comercio and nombre = 'Caleta del Puerto' and activo
  ) then
    raise exception 'No se encontró el local activo Caleta del Puerto esperado para esta prueba';
  end if;

  insert into public.experiencias (
    id, local_id, titulo, descripcion, horario, precio_referencial, imagen_url, activa
  ) values (
    'dcf9f042-a832-4d2f-9025-e7790385517e',
    comercio,
    'Degustación de sabores del puerto',
    'Recorrido de tres preparaciones de cocina marina: entrada, plato principal y postre. La propuesta incluye una presentación de los ingredientes y su preparación por el equipo de cocina.',
    'Viernes y sábado, de 14:00 a 16:00',
    18000,
    null,
    true
  ) on conflict (id) do update set
    local_id = excluded.local_id,
    titulo = excluded.titulo,
    descripcion = excluded.descripcion,
    horario = excluded.horario,
    precio_referencial = excluded.precio_referencial,
    imagen_url = excluded.imagen_url,
    activa = excluded.activa;
end $$;

commit;
