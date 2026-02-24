create table if not exists user_quotas (
    id text primary key,
    project_id text not null,
    user_id text not null,
    group_id text not null,
    spec_json text not null,
    updated_at timestamptz not null,
    updated_by text not null,
    constraint uq_user_quotas_project_group_user unique (project_id, group_id, user_id)
);

create index if not exists idx_user_quotas_project_group_user
    on user_quotas (project_id, group_id, user_id);

create table if not exists default_quotas (
    id text primary key,
    project_id text not null,
    spec_json text not null,
    updated_at timestamptz not null,
    updated_by text not null,
    constraint uq_default_quotas_project unique (project_id)
);
