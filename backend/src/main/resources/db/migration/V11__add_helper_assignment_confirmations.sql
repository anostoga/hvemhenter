alter table assignments
    add column confirmation_status text not null default 'NOT_REQUIRED'
        check (confirmation_status in ('NOT_REQUIRED', 'PENDING', 'ACCEPTED', 'DECLINED')),
    add column confirmation_token_hash text,
    add column confirmation_token_expires_at timestamptz,
    add constraint assignments_confirmation_token_fields_match
        check ((confirmation_token_hash is null) = (confirmation_token_expires_at is null));

create unique index assignments_confirmation_token_hash_idx
    on assignments (confirmation_token_hash)
    where confirmation_token_hash is not null;
