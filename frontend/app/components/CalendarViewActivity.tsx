"use client";

import { useEffect } from "react";
import { api } from "@/lib/api";

export function CalendarViewActivity() {
  useEffect(() => {
    let recorded = false;
    function recordView() {
      if (document.visibilityState !== "visible" || recorded) return;
      recorded = true;
      void api.recordCalendarView().catch((error: unknown) => {
        console.error("Kunne ikke registrere kalendervisning", error);
      });
    }
    recordView();
    document.addEventListener("visibilitychange", recordView);
    return () => document.removeEventListener("visibilitychange", recordView);
  }, []);

  return null;
}
