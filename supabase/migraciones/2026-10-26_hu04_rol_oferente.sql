-- HU-04: rol oferente.
-- El rol vive en app_metadata de auth.users: la app lo lee al iniciar sesión y el usuario no puede
-- modificarlo (a diferencia de user_metadata). Toda cuenta sin rol se trata como consumidora.
-- Las políticas por rol (T-04) pueden usar: (auth.jwt() -> 'app_metadata' ->> 'rol') = 'oferente'.

-- 1. Crear la cuenta del oferente en Authentication → Users → Add user (correo y contraseña,
--    marcando "Auto Confirm User"), o usar una cuenta ya registrada desde la app.
-- 2. Ejecutar en el SQL Editor, cambiando el correo:
update auth.users
set raw_app_meta_data = coalesce(raw_app_meta_data, '{}'::jsonb) || '{"rol": "oferente"}'::jsonb
where email = 'oferente.demo@cocinacapital.cl';

-- Verificación:
select email, raw_app_meta_data ->> 'rol' as rol from auth.users order by email;

-- Para volver a consumidor:
-- update auth.users set raw_app_meta_data = raw_app_meta_data - 'rol' where email = 'oferente.demo@cocinacapital.cl';

-- Si la cuenta tenía la sesión abierta, debe cerrar sesión y volver a entrar para que la app vea el rol.
