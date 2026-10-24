-- HU-07 · Detalle de local. Ejecutar UNA vez en Supabase → SQL Editor sobre una base que ya tiene schema.sql.
-- Agrega la información del local (descripción, dirección, horario) y la tabla de platos del menú.

alter table locales add column if not exists descripcion text not null default '';
alter table locales add column if not exists direccion text;
alter table locales add column if not exists horario text;   -- texto libre, ej: 'Lun a Jue 12:00–22:00 · Vie y Sáb 12:00–00:00'

create table if not exists platos (
  id uuid primary key default gen_random_uuid(),
  local_id uuid not null references locales(id) on delete cascade,
  nombre text not null,
  descripcion text not null default '',
  precio int not null check (precio > 0),      -- pesos chilenos, sin decimales
  destacado boolean not null default false     -- aparece en "Menú destacado"
);

alter table platos enable row level security;
create policy "lectura platos" on platos for select to anon, authenticated using (true);

-- Datos de prueba (ficticios)
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
