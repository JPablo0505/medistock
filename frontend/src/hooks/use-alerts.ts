import apiClient from "@/lib/api-client";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import type { AlertRule, AlertInstance, AlertSeverity, AlertRuleType } from "@/types";

// ─── Alert Rules ─────────────────────────────────────────────────────

export function useAlertRules(type?: string) {
  return useQuery({
    queryKey: ["alert-rules", type],
    queryFn: () =>
      apiClient
        .get<AlertRule[]>("/api/v1/alerts/rules", { params: type ? { type } : {} })
        .then((r) => r.data),
  });
}

export function useAlertRule(id: string) {
  return useQuery({
    queryKey: ["alert-rule", id],
    queryFn: () =>
      apiClient.get<AlertRule>(`/api/v1/alerts/rules/${id}`).then((r) => r.data),
    enabled: !!id,
  });
}

export function useCreateAlertRule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: {
      name: string;
      description?: string;
      type: AlertRuleType;
      config: Record<string, unknown>;
    }) => apiClient.post<AlertRule>("/api/v1/alerts/rules", data).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["alert-rules"] }),
  });
}

export function useToggleAlertRule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiClient.post<AlertRule>(`/api/v1/alerts/rules/${id}/toggle`).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["alert-rules"] }),
  });
}

export function useDeleteAlertRule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiClient.delete(`/api/v1/alerts/rules/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["alert-rules"] }),
  });
}

// ─── Alert Instances ─────────────────────────────────────────────────

export function useAlertInstances(status?: string) {
  return useQuery({
    queryKey: ["alert-instances", status],
    queryFn: () =>
      apiClient
        .get<AlertInstance[]>("/api/v1/alerts/instances", {
          params: { status, page: 0, size: 100 },
        })
        .then((r) => r.data),
  });
}

export function useActiveAlertCount() {
  return useQuery({
    queryKey: ["alert-count"],
    queryFn: () =>
      apiClient.get<number>("/api/v1/alerts/instances/count").then((r) => r.data),
    refetchInterval: 30000, // every 30s
  });
}

export function useAcknowledgeAlert() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiClient.post<AlertInstance>(`/api/v1/alerts/instances/${id}/acknowledge`).then((r) => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["alert-instances"] });
      qc.invalidateQueries({ queryKey: ["alert-count"] });
    },
  });
}

export function useResolveAlert() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiClient.post<AlertInstance>(`/api/v1/alerts/instances/${id}/resolve`).then((r) => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["alert-instances"] });
      qc.invalidateQueries({ queryKey: ["alert-count"] });
    },
  });
}

// ─── Evaluate ────────────────────────────────────────────────────────

export function useEvaluateAlerts() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiClient.post<number>("/api/v1/alerts/evaluate").then((r) => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["alert-instances"] });
      qc.invalidateQueries({ queryKey: ["alert-count"] });
    },
  });
}
