-- Varios locales por oferente (cambio sobre T-04 / HU-25). Ejecutar UNA vez en Supabase → SQL Editor,
-- después de 2026-10-26_t04_perfiles_roles_y_local_oferente.sql. Se puede volver a ejecutar.
--
-- T-04 permitía un solo local por oferente. Ahora un oferente puede tener varias tiendas; las
-- políticas no cambian, porque ya comparan cada fila con su `oferente_id`.

drop index if exists public.locales_un_local_por_oferente;
create index if not exists locales_oferente_idx on public.locales (oferente_id) where oferente_id is not null;

-- Verificación: locales por oferente.
select u.email, count(l.id) as locales
from public.perfiles p
join auth.users u on u.id = p.id
left join public.locales l on l.oferente_id = p.id
where p.rol = 'oferente'
group by u.email
order by u.email;
