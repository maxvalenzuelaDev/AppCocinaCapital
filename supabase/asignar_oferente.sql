-- HU-04 · Convertir una cuenta en oferente (o devolverla a consumidor).
-- Requiere la migración 2026-10-26_t04_perfiles_roles_y_local_oferente.sql.
-- El rol se cambia en public.perfiles; un trigger lo copia a app_metadata, que es lo que lee la app.

-- 1. Crear la cuenta en Authentication → Users → Add user (correo y contraseña, marcando
--    "Auto Confirm User"), o usar una cuenta ya registrada desde la app.
-- 2. Ejecutar en el SQL Editor, cambiando el correo:
update public.perfiles
set rol = 'oferente'
where id = (select id from auth.users where email = 'oferente@gmail.com');

-- 3. (Opcional) Asignarle un local que ya existe, en vez de que lo cree desde la app:
-- update public.locales
-- set oferente_id = (select id from auth.users where email = 'oferente.demo@cocinacapital.cl')
-- where nombre = 'Caleta del Puerto';

-- Verificación:
select u.email, p.rol, l.nombre as local
from auth.users u
join public.perfiles p on p.id = u.id
left join public.locales l on l.oferente_id = u.id
order by u.email;

-- Para volver a consumidor:
-- update public.perfiles set rol = 'consumidor'
-- where id = (select id from auth.users where email = 'oferente@gmail.com');

-- Si la cuenta tenía la sesión abierta, debe cerrar sesión y volver a entrar para que la app vea el rol.
