import assert from "node:assert/strict";
import { test } from "node:test";
import { frontpageWeek } from "./frontpage-week.ts";

const currentWeek = ["2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29", "2026-10-30"];
const nextWeek = ["2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05", "2026-11-06"];

for (const [label, date, days, isNextWeek] of [
  ["Friday before 17:00", new Date(2026, 9, 30, 16, 59, 59), currentWeek, false],
  ["Friday at 17:00", new Date(2026, 9, 30, 17), nextWeek, true],
  ["Saturday morning", new Date(2026, 9, 31, 8), nextWeek, true],
  ["Sunday evening", new Date(2026, 10, 1, 23, 59), nextWeek, true],
  ["Monday morning", new Date(2026, 10, 2, 0), nextWeek, false],
  ["Thursday after 17:00", new Date(2026, 9, 29, 18), currentWeek, false],
]) {
  test(label, () => {
    assert.deepEqual(frontpageWeek(date), { days, isNextWeek });
  });
}

test("rolls into the next year", () => {
  assert.deepEqual(frontpageWeek(new Date(2027, 11, 31, 17)), {
    days: ["2028-01-03", "2028-01-04", "2028-01-05", "2028-01-06", "2028-01-07"],
    isNextWeek: true,
  });
});
