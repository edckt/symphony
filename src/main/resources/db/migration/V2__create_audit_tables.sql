create table if not exists quota_audit (
    event_id text primary key,
    project_id text not null,
    user_id text not null,
    group_id text not null,
    action text not null,
    at timestamptz not null,
    actor_principal text not null,
    old_spec_json text null,
    new_spec_json text null
);

create index if not exists idx_quota_audit_project_group_user_at
on quota_audit (project_id, group_id, user_id, at desc);