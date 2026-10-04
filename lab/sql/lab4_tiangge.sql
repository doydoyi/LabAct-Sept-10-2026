-- Lab 4: Tiangge marketplace channel tables.
-- Run this in the Supabase SQL editor AFTER schema.sql and
-- lab3_supplier_orders.sql. It only adds new tables - your existing
-- inventory/orders/supplier_orders data is untouched.
--
-- WARNING: only run it ONCE for your live shop. Re-running it resets the
-- feed cursor and forgets which Tiangge events were already processed,
-- which the marketplace grades as a cursor reset / duplicate processing.

drop table if exists tiangge_orders cascade;
drop table if exists tiangge_processed_events cascade;
drop table if exists tiangge_feed_cursor cascade;

-- Where we stopped reading the order feed (single row, id = 1).
create table tiangge_feed_cursor (
    id          integer      primary key,
    last_seq    bigint       not null,
    updated_at  timestamptz  not null default now()
);
insert into tiangge_feed_cursor (id, last_seq) values (1, 0);

-- Every feed eventId we have handled (redeliveries are skipped).
create table tiangge_processed_events (
    event_id          varchar(64)  primary key,
    seq               bigint       not null,
    type              varchar(30)  not null,
    tiangge_order_id  varchar(40),
    processed_at      timestamptz  not null default now()
);

-- One Tiangge order <-> one order in our own Order module, plus what we
-- still owe Tiangge (decision / backorder resolution / cancel confirmation).
create table tiangge_orders (
    tiangge_order_id     varchar(40)  primary key,
    shop_order_id        bigint       references orders(order_id),
    decision             varchar(20),
    decision_reason      varchar(200),
    decision_reported    boolean      not null default false,
    decision_deadline    timestamptz,
    resolution           varchar(20),
    resolution_reported  boolean      not null default false,
    cancel_requested     boolean      not null default false,
    cancel_confirmed     boolean      not null default false,
    created_at           timestamptz  not null default now(),
    updated_at           timestamptz  not null default now()
);

create index idx_tiangge_orders_decision on tiangge_orders(decision);
