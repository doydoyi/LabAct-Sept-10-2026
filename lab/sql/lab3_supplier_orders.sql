-- Lab 3: adds the supplier_orders table on top of Lab 2's schema.
-- Run this in the Supabase SQL editor AFTER Lab 2's schema.sql.
-- Unlike schema.sql, this does NOT drop existing tables - it only adds
-- the new one, so your Lab 2 inventory/orders/notifications data is safe.

drop table if exists supplier_orders cascade;

create table supplier_orders (
    id                 bigserial primary key,
    product_id         varchar(20)  not null references inventory(product_id),
    buyer_ref          varchar(40)  unique,
    request_id         varchar(80)  not null unique,
    supplier_sku       varchar(50)  not null,
    qty                integer      not null,
    pack_size          integer      not null,
    uom                varchar(10),
    po_number          varchar(50),
    status             varchar(20)  not null,
    legacy_status_code integer,
    error_code         varchar(20),
    error_message      varchar(500),
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now()
);

create index idx_supplier_orders_status on supplier_orders(status);
create index idx_supplier_orders_product on supplier_orders(product_id);
