-- Assignments belong to the parent who owns them. Deleting an account must
-- remove its past assignments as well as the future assignments cleaned up
-- explicitly by AccountRoutes for Google Calendar synchronization.
alter table assignments drop constraint assignments_parent_id_fkey;
alter table assignments
    add constraint assignments_parent_id_fkey
    foreign key (parent_id) references parents(id) on delete cascade;
