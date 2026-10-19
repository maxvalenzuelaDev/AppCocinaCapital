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
  activo boolean not null default true
);

create table if not exists promociones (
  id uuid primary key default gen_random_uuid(),
  local_id uuid not null references locales(id) on delete cascade,
  titulo text not null,
  descripcion text not null default '',
  descuento_porcentaje int not null check (descuento_porcentaje between 1 and 100),
  vigente_desde timestamptz not null default now(),
  vigente_hasta timestamptz not null,
  activa boolean not null default true
);

-- Seguridad (RLS): lectura pública del catálogo; escritura solo desde el panel/servicio.
alter table categorias enable row level security;
alter table locales enable row level security;
alter table promociones enable row level security;
create policy "lectura categorias" on categorias for select to anon, authenticated using (true);
create policy "lectura locales" on locales for select to anon, authenticated using (activo);
create policy "lectura promociones" on promociones for select to anon, authenticated using (activa);

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
