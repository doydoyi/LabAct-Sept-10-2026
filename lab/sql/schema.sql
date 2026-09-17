-- Lab 2: full schema (re)creation script.
-- Run this in the Supabase SQL editor. Safe to re-run any time: it drops
-- the previous Lab 1/Lab 2 tables first, then recreates everything from
-- scratch, including seed data. Do NOT hand-edit tables in the Supabase
-- UI - this script is the single source of truth for the schema.

drop table if exists order_items cascade;
drop table if exists orders cascade;
drop table if exists notifications cascade;
drop table if exists inventory cascade;

create table inventory (
    product_id  varchar(20)  primary key,
    name        varchar(100) not null,
    stock       integer      not null check (stock >= 0)
);

-- An order no longer carries product_id/quantity directly - those moved to
-- order_items, since an order can now have multiple line items. status now
-- also supports CANCELLED alongside CONFIRMED/REJECTED.
create table orders (
    order_id    bigserial primary key,
    status      varchar(20)  not null,
    reason      varchar(255),
    created_at  timestamptz  not null default now()
);

create table order_items (
    order_item_id bigserial primary key,
    order_id      bigint      not null references orders(order_id) on delete cascade,
    product_id    varchar(20) not null references inventory(product_id),
    quantity      integer     not null
);

create table notifications (
    notification_id bigserial primary key,
    message          varchar(500) not null,
    created_at       timestamptz  not null default now()
);

-- Seed data, unchanged from Lab 1.
insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0);
