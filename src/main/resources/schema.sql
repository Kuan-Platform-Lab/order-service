create table if not exists orders (
  id          bigserial primary key,
  customer_id text not null,
  amount      numeric(18,2) not null,
  status      text not null,
  created_at  timestamptz not null default now()
);
