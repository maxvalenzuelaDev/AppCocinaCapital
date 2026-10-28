-- HU-34 · Carga el dataset ficticio de perfiles de consumo y sus interacciones.
-- Ejecutar en Supabase → SQL Editor después de migraciones/2026-10-28_hu34_dataset_perfiles_consumo.sql
-- y de cargar el catálogo de prueba (schema.sql). Reemplaza solo las filas de las tablas dataset_*.
--
-- Usa una semilla fija: con el mismo catálogo, el dataset sale igual cada vez. Las interacciones
-- apuntan a los locales y promociones que existan al ejecutarlo.

begin;

truncate public.dataset_interacciones, public.dataset_perfiles_consumo restart identity;

select setseed(0.34);

-- 40 perfiles. Los gustos se toman de las categorías reales del catálogo. El `where n > 0` hace que
-- cada subconsulta dependa del perfil; si no, Postgres la calcula una vez y todos saldrían iguales.
insert into public.dataset_perfiles_consumo
  (id, comuna, rango_edad, categorias_preferidas, rango_precio, presupuesto_persona, dias_preferidos, franja_preferida, visitas_mes)
select
  'P-' || lpad(n::text, 3, '0'),
  (array['Valparaíso', 'Viña del Mar', 'Quilpué', 'Villa Alemana', 'Concón'])[1 + floor(random() * 5)::int],
  (array['18-24', '25-34', '35-44', '45-54', '55+'])[1 + floor(random() * 5)::int],
  (select array_agg(nombre order by nombre) from (
     select c.nombre from public.categorias c where n > 0 order by random() limit 1 + floor(random() * 3)::int) gustos),
  rango.valor,
  case rango.valor when 'ECONOMICO' then 5000 when 'MEDIO' then 10000 else 18000 end
    + (floor(random() * 6)::int * 1000),
  (select array_agg(d::smallint order by d) from (
     select d from generate_series(1, 7) d where n > 0 order by random() limit 2 + floor(random() * 3)::int) dias),
  (array['desayuno', 'almuerzo', 'tarde', 'cena'])[1 + floor(random() * 4)::int],
  1 + floor(random() * 12)::int
from generate_series(1, 40) n
cross join lateral (
  select (array['ECONOMICO', 'MEDIO', 'MEDIO', 'PREMIUM'])[1 + floor(random() * 4)::int] as valor
  where n > 0  -- se evalúa por perfil
) rango;

-- Unas 10 interacciones por perfil en los últimos 60 días: más vistas que canjes, y de preferencia
-- en locales de las categorías que le gustan.
insert into public.dataset_interacciones (perfil_id, tipo, local_id, promocion_id, termino, ocurrido_en)
select
  p.id,
  i.tipo,
  l.id,
  case when i.tipo in ('vista_promocion', 'canje')
       then (select pr.id from public.promociones pr where pr.local_id = l.id order by random() limit 1) end,
  case when i.tipo = 'busqueda' then lower(coalesce(p.categorias_preferidas[1], c.nombre)) end,
  now() - (random() * interval '60 days')
from public.dataset_perfiles_consumo p
cross join lateral generate_series(1, 6 + floor(random() * 9)::int) k
cross join lateral (
  select case
    when r < 0.20 then 'busqueda'
    when r < 0.55 then 'vista_local'
    when r < 0.90 then 'vista_promocion'
    else 'canje' end as tipo
  -- Depende del perfil y de k: si dependiera solo de k, Postgres reutilizaría el mismo azar
  -- (Memoize) y la interacción número k sería del mismo tipo en todos los perfiles.
  from (select random() as r where k > 0 and p.id is not null offset 0) azar
) i
cross join lateral (
  -- 75 % de las veces, un local de sus categorías preferidas; si no, cualquiera.
  select lo.id, lo.categoria_id from public.locales lo
  join public.categorias ca on ca.id = lo.categoria_id
  where lo.activo and (random() > 0.75 or ca.nombre = any (p.categorias_preferidas) or k < 0)
  order by random() limit 1
) l
join public.categorias c on c.id = l.categoria_id;

-- Un canje sin promoción del local no tiene sentido: pasa a vista del local.
update public.dataset_interacciones set tipo = 'vista_local'
where tipo in ('canje', 'vista_promocion') and promocion_id is null;

commit;

-- Resumen
select tipo, count(*) from public.dataset_interacciones group by tipo order by tipo;
select rango_precio, count(*) from public.dataset_perfiles_consumo group by rango_precio order by rango_precio;
