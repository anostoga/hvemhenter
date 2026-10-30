export function frontpageWeek(now: Date) {
  const day = now.getDay();
  const isNextWeek = day === 0 || day === 6 || (day === 5 && now.getHours() >= 17);
  const monday = new Date(now);
  monday.setDate(now.getDate() + (day === 0 ? -6 : 1 - day) + (isNextWeek ? 7 : 0));

  const days = Array.from({ length: 5 }, (_, index) => {
    const date = new Date(monday);
    date.setDate(monday.getDate() + index);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
  });

  return { days, isNextWeek };
}
