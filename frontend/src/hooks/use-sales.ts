import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import apiClient from "@/lib/api-client";
import type {
  SaleResponse,
  SaleSummary,
  CreateSaleRequest,
} from "@/types";

export function useSales(filters?: {
  status?: string;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  size?: number;
}) {
  return useQuery({
    queryKey: ["sales", filters],
    queryFn: () => {
      const params: Record<string, string | boolean | number | undefined> = {};
      if (filters?.status) params.status = filters.status;
      if (filters?.dateFrom) params.dateFrom = filters.dateFrom;
      if (filters?.dateTo) params.dateTo = filters.dateTo;
      params.page = filters?.page ?? 0;
      params.size = filters?.size ?? 20;

      return apiClient.get<SaleSummary[]>("/api/v1/sales", { params }).then((r) => r.data);
    },
  });
}

export function useSale(id: string) {
  return useQuery({
    queryKey: ["sale", id],
    queryFn: () => apiClient.get<SaleResponse>(`/api/v1/sales/${id}`).then((r) => r.data),
    enabled: !!id,
  });
}

export function useCreateSale() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateSaleRequest) =>
      apiClient.post<SaleResponse>("/api/v1/sales", data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["sales"] });
    },
  });
}

export function useVoidSale() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      apiClient.post<SaleResponse>(`/api/v1/sales/${id}/void`, { reason }).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["sales"] });
    },
  });
}
