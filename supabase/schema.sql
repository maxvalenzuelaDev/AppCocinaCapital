-- Ejecutar en Supabase → SQL Editor. Crea las tablas que lee el Home y carga datos FICTICIOS.

create table if not exists categorias (
  id uuid primary key default gen_random_uuid(),
  nombre text not null unique
);

create table if not exists locales (
  id uuid primary key default gen_random_uuid(),
  nombre text not null,
  categoria_id uuid not null references categorias(id),
  comuna text not null,
  imagen_url text,                        -- URL pública del bucket de Storage
  valoracion numeric(2,1) not null default 0 check (valoracion between 0 and 5),
  latitud double precision,
  longitud double precision,
  activo boolean not null default true,
  descripcion text not null default '',
  direccion text,
  horario text,                           -- texto libre, ej: 'Lun a Jue 12:00–22:00 · Vie y Sáb 12:00–00:00'
  ticket_promedio_persona bigint check (ticket_promedio_persona > 0), -- CLP por persona, declarado por el comercio; null si no está informado
  oferente_id uuid references auth.users(id) on delete set null -- dueño del local (T-04); perfiles y políticas en migraciones/2026-10-26_t04_*
);

create table if not exists platos (
  id uuid primary key default gen_random_uuid(),
  local_id uuid not null references locales(id) on delete cascade,
  nombre text not null,
  descripcion text not null default '',
  precio int not null check (precio > 0),      -- pesos chilenos, sin decimales
  destacado boolean not null default false     -- aparece en "Menú destacado"
);

create table if not exists promociones (
  id uuid primary key default gen_random_uuid(),
  local_id uuid not null references locales(id) on delete cascade,
  titulo text not null,
  descripcion text not null default '',
  descuento_porcentaje int not null check (descuento_porcentaje between 1 and 100),
  vigente_desde timestamptz not null default now(),
  vigente_hasta timestamptz not null,
  activa boolean not null default true,
  -- HU-27: condiciones, días (1 = lunes … 7 = domingo), franja horaria y cupos (null = sin límite).
  -- Restricciones, trigger de cupos y permisos en migraciones/2026-10-27_hu27_*
  condiciones text not null default '',
  dias_semana smallint[],
  hora_inicio time,
  hora_fin time,
  cupos_total int,
  cupos_disponibles int
);

-- Billetera (T-02): un beneficio por consumidor y promoción, con su código de canje. Solo se lee;
-- adquirir, liberar y canjear pasan por las funciones de migraciones/2026-10-28_t03_rpc_beneficios.sql.
-- El código lo genera public.generar_codigo_beneficio() (migraciones/2026-10-28_t02_*).
create table if not exists beneficios_usuario (
  id uuid primary key default gen_random_uuid(),
  usuario_id uuid not null references auth.users(id) on delete cascade,
  promocion_id uuid not null references promociones(id) on delete cascade,
  guardado_en timestamptz not null default now(),
  codigo text not null unique,                  -- 8 caracteres; se muestra como texto y como QR
  estado text not null default 'vigente',       -- vigente | canjeado ("vencido" se calcula con la promoción)
  canjeado_en timestamptz,
  constraint beneficio_usuario_unico unique (usuario_id, promocion_id),
  constraint beneficios_usuario_estado_valido
    check (estado in ('vigente', 'canjeado') and (estado = 'canjeado') = (canjeado_en is not null))
);

-- Preferencias del consumidor (HU-02, T-06): copia de lo guardado en Room para usarlas en otro teléfono.
-- Consentimientos: historial de aceptaciones y revocaciones con fecha (HU-03). Políticas, trigger y
-- permisos en migraciones/2026-10-28_t06_preferencias_y_consentimientos.sql
create table if not exists preferencias_usuario (
  usuario_id uuid primary key default auth.uid() references auth.users(id) on delete cascade,
  categorias uuid[] not null default '{}',     -- ids de categorias elegidas
  rango_precio text not null default 'MEDIO',  -- ECONOMICO | MEDIO | PREMIUM
  notificaciones boolean not null default false,
  solo_cercanas boolean not null default false,
  solo_categorias_favoritas boolean not null default false,
  tema text not null default 'SISTEMA',        -- SISTEMA | CLARO | OSCURO
  consentimiento boolean not null default false,
  consentimiento_fecha timestamptz,
  consentimiento_respondido boolean not null default false,
  actualizado_en timestamptz not null default now()
);

create table if not exists consentimientos (
  id bigint generated always as identity primary key,
  usuario_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  acepto boolean not null,
  registrado_en timestamptz not null default now()
);

-- Seguridad (RLS): lectura pública del catálogo; escritura solo desde el panel/servicio.
alter table categorias enable row level security;
alter table locales enable row level security;
alter table promociones enable row level security;
alter table platos enable row level security;
create policy "lectura categorias" on categorias for select to anon, authenticated using (true);
create policy "lectura locales" on locales for select to anon, authenticated using (activo);
create policy "lectura promociones" on promociones for select to anon, authenticated using (activa);
create policy "lectura platos" on platos for select to anon, authenticated using (true);
alter table beneficios_usuario enable row level security;
create policy "Ver mis beneficios" on beneficios_usuario for select to authenticated using (auth.uid() = usuario_id);

-- Datos de prueba (ficticios)
insert into categorias (nombre) values
  ('Cocina Marina'), ('Cafetería'), ('Burgers'), ('Pizzería'), ('Sushi'), ('Pastelería');

insert into locales (nombre, categoria_id, comuna, valoracion, latitud, longitud)
select v.nombre, c.id, v.comuna, v.val, v.lat, v.lon
from (values
  ('Caleta del Puerto', 'Cocina Marina', 'Valparaíso', 4.7, -33.0393, -71.6273),
  ('Café Cerro Alegre', 'Cafetería', 'Valparaíso', 4.5, -33.0425, -71.6285),
  ('Burger Pacífico', 'Burgers', 'Viña del Mar', 4.2, -33.0245, -71.5518),
  ('Sushi Muelle', 'Sushi', 'Valparaíso', 4.6, -33.0360, -71.6290)
) as v(nombre, cat, comuna, val, lat, lon)
join categorias c on c.nombre = v.cat;

insert into promociones (local_id, titulo, descripcion, descuento_porcentaje, vigente_hasta)
select l.id, p.titulo, p.descripcion, p.pct, now() + p.dur
from (values
  ('Caleta del Puerto', '2x1 en pastel de jaiba', 'Válido en horario de almuerzo, de lunes a jueves.', 50, interval '3 days'),
  ('Café Cerro Alegre', 'Café + kuchen', 'Café de especialidad con kuchen del día.', 30, interval '2 days'),
  ('Burger Pacífico', 'Combo burger tarde', 'Burger clásica, papas y bebida entre 15:00 y 18:00.', 25, interval '5 days')
) as p(local, titulo, descripcion, pct, dur)
join locales l on l.nombre = p.local;

-- Información del detalle del local y menú (HU-07)
update locales l set descripcion = v.descripcion, direccion = v.direccion, horario = v.horario
from (values
  ('Caleta del Puerto', 'Pescados y mariscos frescos de la caleta, con vista a la bahía.', 'Av. Errázuriz 1120', 'Lun a Dom 12:00–17:00'),
  ('Café Cerro Alegre', 'Café de especialidad y pastelería casera en pleno cerro.', 'Calle Templeman 345', 'Lun a Sáb 09:00–20:00'),
  ('Burger Pacífico', 'Hamburguesas de carne seleccionada y papas cortadas a mano.', 'Av. San Martín 678', 'Mar a Dom 13:00–23:00'),
  ('Sushi Muelle', 'Sushi y handrolls preparados al momento.', 'Calle Blanco 980', 'Lun a Sáb 12:30–22:30')
) as v(nombre, descripcion, direccion, horario)
where l.nombre = v.nombre;

insert into platos (local_id, nombre, descripcion, precio, destacado)
select l.id, p.nombre, p.descripcion, p.precio, p.destacado
from (values
  ('Caleta del Puerto', 'Pastel de jaiba', 'Gratinado con queso, receta de la casa.', 12900, true),
  ('Caleta del Puerto', 'Reineta a la plancha', 'Con papas cocidas y ensalada chilena.', 10900, true),
  ('Caleta del Puerto', 'Ceviche mixto', 'Pescado, camarón y pulpo.', 9900, false),
  ('Café Cerro Alegre', 'Café de especialidad', 'Grano de origen único, método a elección.', 3200, true),
  ('Café Cerro Alegre', 'Kuchen de frambuesa', 'Porción individual.', 3500, true),
  ('Burger Pacífico', 'Burger clásica', 'Carne 180 g, queso cheddar, tomate y lechuga.', 8900, true),
  ('Burger Pacífico', 'Papas rústicas', 'Con alioli de la casa.', 3900, true),
  ('Sushi Muelle', 'Tabla Muelle 20 piezas', 'Selección del chef.', 15900, true),
  ('Sushi Muelle', 'Handroll de salmón', 'Salmón, queso crema y cebollín.', 4500, true)
) as p(local, nombre, descripcion, precio, destacado)
join locales l on l.nombre = p.local;
