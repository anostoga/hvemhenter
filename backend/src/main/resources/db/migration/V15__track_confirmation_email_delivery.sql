alter table assignments
    add column confirmation_email_sent boolean not null default false;

update assignments
set confirmation_email_sent = confirmation_link_issued;
