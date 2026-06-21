import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import apiClient from "@/lib/api-client";
import type { Batch, BatchDetail, StockProjection } from "@/types";
import { toast } from "sonner";

// ─── Query Keys ──────────────────────────────────────────────────────────

export const batchesKeys = {
  all: ["batches"] as const,
  lists: () => [...batchesKeys.all, "list"] as const,
  list: () => [...batchesKeys.lists()] as const,
  details: () => [...batchesKeys.all, "detail"] as const,
  detail: (id: string) => [...batchesKeys.details(), id] as const,
  stock: (productId: string) => [...batchesKeys.all, "stock", productId] as const,
};

// ─── Batch Queries ───────────────────────────────────────────────────────

export function useBatches() {
  return useQuery({
    queryKey: batchesKeys.list(),
    queryFn: async () => {
      const { data } = await apiClient.get<Batch[]>("/api/v1/batches");
      return data;
    },
  });
}

export function useBatch(id: string) {
  return useQuery({
    queryKey: batchesKeys.detail(id),
    queryFn: async () => {
      const { data } = await apiClient.get<BatchDetail>(
        `/api/v1/batches/${id}`
      );
      return data;
    },
    enabled: !!id,
  });
}

export function useStockProjection(productId: string) {
  return useQuery({
    queryKey: batchesKeys.stock(productId),
    queryFn: async () => {
      const { data } = await apiClient.get<StockProjection>(
        `/api/v1/batches/stock/${productId}`
      );
      return data;
    },
    enabled: !!productId,
  });
}

// ─── Batch Mutations ─────────────────────────────────────────────────────

export interface CreateBatchInput {
  productId: string;
  batchCode: string;
  quantity: number;
  expirationDate: string;
  manufacturingDate?: string;
  costPerUnit: number;
  category?: string;
  notes?: string;
}

export function useCreateBatch() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: CreateBatchInput) => {
      const { data } = await apiClient.post<Batch>(
        "/api/v1/batches",
        input
      );
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: batchesKeys.lists() });
      queryClient.invalidateQueries({ queryKey: ["products"] });
      toast.success("Lote creado exitosamente");
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Error al crear lote");
    },
  });
}

export function useQuarantineBatch() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, reason }: { id: string; reason: string }) => {
      const { data } = await apiClient.post<Batch>(
        `/api/v1/batches/${id}/quarantine`,
        { reason }
      );
      return data;
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: batchesKeys.lists() });
      queryClient.invalidateQueries({
        queryKey: batchesKeys.detail(data.id),
      });
      toast.success("Lote puesto en cuarentena");
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Error al poner en cuarentena");
    },
  });
}

export function useReleaseFromQuarantine() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, reason }: { id: string; reason: string }) => {
      const { data } = await apiClient.post<Batch>(
        `/api/v1/batches/${id}/release`,
        { reason }
      );
      return data;
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: batchesKeys.lists() });
      queryClient.invalidateQueries({
        queryKey: batchesKeys.detail(data.id),
      });
      toast.success("Lote liberado de cuarentena");
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Error al liberar de cuarentena");
    },
  });
}

export function useDisposeBatch() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, reason }: { id: string; reason: string }) => {
      const { data } = await apiClient.post<Batch>(
        `/api/v1/batches/${id}/dispose`,
        { reason }
      );
      return data;
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: batchesKeys.lists() });
      queryClient.invalidateQueries({
        queryKey: batchesKeys.detail(data.id),
      });
      toast.success("Lote desechado");
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Error al desechar lote");
    },
  });
}

export interface AdjustStockInput {
  id: string;
  newQuantity: number;
  reason: string;
}

export function useAdjustStock() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, newQuantity, reason }: AdjustStockInput) => {
      const { data } = await apiClient.post<Batch>(
        `/api/v1/batches/${id}/adjust`,
        { newQuantity, reason }
      );
      return data;
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: batchesKeys.lists() });
      queryClient.invalidateQueries({
        queryKey: batchesKeys.detail(data.id),
      });
      toast.success("Stock ajustado exitosamente");
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Error al ajustar stock");
    },
  });
}
