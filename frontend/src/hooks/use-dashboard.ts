import { useQuery } from "@tanstack/react-query";
import apiClient from "@/lib/api-client";

// ─── Types ──────────────────────────────────────────────────────────────

export interface DashboardSummary {
  totalProducts: number;
  activeProducts: number;
  activeBatches: number;
  activeAlerts: number;
  expiringSoonBatches: number;
}

// ─── Query Keys ─────────────────────────────────────────────────────────

export const dashboardKeys = {
  all: ["dashboard"] as const,
  summary: () => [...dashboardKeys.all, "summary"] as const,
};

// ─── Queries ────────────────────────────────────────────────────────────

export function useDashboardSummary() {
  return useQuery({
    queryKey: dashboardKeys.summary(),
    queryFn: async () => {
      const { data } = await apiClient.get<DashboardSummary>(
        "/api/v1/dashboard/summary"
      );
      return data;
    },
  });
}
