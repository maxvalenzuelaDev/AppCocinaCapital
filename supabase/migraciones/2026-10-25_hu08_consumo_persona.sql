-- HU-08: consumo promedio por persona que declara el comercio (ejemplo del cliente: Ticket X pp).
-- Para una base ya creada, ejecutar en el SQL Editor del proyecto del equipo.
-- Se deja NULL en los locales existentes; no hay cifras confirmadas para completarlos.
alter table public.locales
  add column if not exists ticket_promedio_persona bigint check (ticket_promedio_persona > 0);

comment on column public.locales.ticket_promedio_persona is
  'Consumo promedio por persona en CLP, declarado por el comercio. NULL si no informado. No calcular a partir de platos ni promociones.';
