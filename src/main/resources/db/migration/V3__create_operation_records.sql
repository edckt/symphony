create table if not exists operation_records (
    id           text primary key,  -- Base64url-encoded GCP LRO name
    lro_name     text not null,      -- Raw GCP LRO name
    initiated_by text not null,      -- principal who started the operation
    project_id   text,
    created_at   timestamptz not null
);
