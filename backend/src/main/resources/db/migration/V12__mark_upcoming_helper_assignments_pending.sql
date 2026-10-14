update assignments a
set confirmation_status = 'PENDING'
from parents p
where a.parent_id = p.id
  and p.is_helper = true
  and a.date >= current_date;
