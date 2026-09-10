-- Run this in the Supabase SQL editor (or via psql) before starting the backend.

create table if not exists inventory (
    product_id  varchar(20)  primary key,
    name        varchar(100) not null,
    stock       integer      not null check (stock >= 0)
);

create table if not exists orders (
    order_id    bigserial primary key,
    product_id  varchar(20)  not null references inventory(product_id),
    quantity    integer      not null,
    status      varchar(20)  not null,
    reason      varchar(255),
    created_at  timestamptz  not null default now()
);

-- Seed data as specified in the lab instructions.
insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0)
on conflict (product_id) do update
    set name = excluded.name,
        stock = excluded.stock;
