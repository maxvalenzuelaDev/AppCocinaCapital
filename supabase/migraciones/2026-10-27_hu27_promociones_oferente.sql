-- HU-27 · Crear promociones dirigidas (oferente). Ejecutar UNA vez en Supabase → SQL Editor,
-- después de 2026-10-26_t04_perfiles_roles_y_local_oferente.sql. Se puede volver a ejecutar.
--
-- Agrega a `promociones` lo que el oferente define al crearla: condiciones, días y franja horaria
-- (para sus horarios de baja demanda) y cupos. Los nombres de cupos son los acordados en T-02
-- (billetera): `cupos_total` lo fija el oferente y `cupos_disponibles` lo descuentan las funciones de
-- canje (T-03). Al crear la promoción, los disponibles parten iguales al total.

alter table public.promociones add column if not exists condiciones text not null default '';
-- Días de la semana en que aplica (1 = lunes … 7 = domingo). NULL = todos los días.
alter table public.promociones add column if not exists dias_semana smallint[];
-- Franja horaria en que aplica. Ambas NULL = todo el horario del local.
alter table public.promociones add column if not exists hora_inicio time;
alter table public.promociones add column if not exists hora_fin time;
-- Cupos. NULL = sin límite.
alter table public.promociones add column if not exists cupos_total int;
alter table public.promociones add column if not exists cupos_disponibles int;

alter table public.promociones drop constraint if exists promociones_dias_validos;
alter table public.promociones add constraint promociones_dias_validos
  check (dias_semana is null or (cardinality(dias_semana) > 0 and dias_semana <@ array[1, 2, 3, 4, 5, 6, 7]::smallint[]));

alter table public.promociones drop constraint if exists promociones_horario_valido;
alter table public.promociones add constraint promociones_horario_valido
  check ((hora_inicio is null) = (hora_fin is null) and (hora_inicio is null or hora_inicio < hora_fin));

alter table public.promociones drop constraint if exists promociones_vigencia_valida;
alter table public.promociones add constraint promociones_vigencia_valida check (vigente_hasta > vigente_desde);

alter table public.promociones drop constraint if exists promociones_cupos_validos;
alter table public.promociones add constraint promociones_cupos_validos
  check ((cupos_total is null or cupos_total > 0) and (cupos_disponibles is null or cupos_disponibles >= 0));

-- Al crear una promoción, los cupos disponibles parten iguales al total (la app no los envía).
create or replace function public.iniciar_cupos_promocion()
returns trigger language plpgsql set search_path = '' as $$
begin
  new.cupos_disponibles := new.cupos_total;
  return new;
end;
$$;

drop trigger if exists promociones_iniciar_cupos on public.promociones;
create trigger promociones_iniciar_cupos
  before insert on public.promociones
  for each row execute function public.iniciar_cupos_promocion();

-- El oferente escribe solo los datos de la promoción; los cupos disponibles no (los maneja el canje).
-- Las filas que puede tocar ya las limita la política "oferente gestiona sus promociones" (T-04).
revoke insert, update on public.promociones from authenticated;
grant insert (local_id, titulo, descripcion, condiciones, descuento_porcentaje, vigente_desde, vigente_hasta,
              dias_semana, hora_inicio, hora_fin, cupos_total, activa)
  on public.promociones to authenticated;
grant update (titulo, descripcion, condiciones, descuento_porcentaje, vigente_desde, vigente_hasta,
              dias_semana, hora_inicio, hora_fin, activa)
  on public.promociones to authenticated;

-- Verificación: columnas nuevas.
select column_name, data_type from information_schema.columns
where table_schema = 'public' and table_name = 'promociones'
order by ordinal_position;
