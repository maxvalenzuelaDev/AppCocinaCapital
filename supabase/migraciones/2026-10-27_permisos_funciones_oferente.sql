-- Permisos de las funciones de T-04 y T-05 (avisos del asesor de seguridad de Supabase).
-- Ejecutar después de 2026-10-27_t05_storage_fotos_locales.sql. Se puede volver a ejecutar.
--
-- Por defecto Postgres deja ejecutar cualquier función a todos (PUBLIC), y la API las expone en
-- /rest/v1/rpc/. Estas no deben llamarse desde la app:
--   • crear_perfil y sincronizar_rol son triggers: se disparan solos y no necesitan este permiso.
--   • es_oferente y es_carpeta_de_mi_local las usan las políticas de RLS, que se evalúan con el rol
--     de quien tiene sesión; por eso `authenticated` conserva el permiso y `anon` no.

revoke execute on function public.crear_perfil() from public, anon, authenticated;
revoke execute on function public.sincronizar_rol() from public, anon, authenticated;

revoke execute on function public.es_oferente() from public, anon;
revoke execute on function public.es_carpeta_de_mi_local(text) from public, anon;
grant execute on function public.es_oferente() to authenticated;
grant execute on function public.es_carpeta_de_mi_local(text) to authenticated;
