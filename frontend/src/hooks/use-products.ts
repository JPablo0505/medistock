import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import apiClient from "@/lib/api-client";
import type { Product, Category } from "@/types";

// ─── Query Keys ──────────────────────────────────────────────────────────

export const productsKeys = {
  all: ["products"] as const,
  lists: () => [...productsKeys.all, "list"] as const,
  list: (filters?: { active?: boolean }) =>
    [...productsKeys.lists(), filters] as const,
  details: () => [...productsKeys.all, "detail"] as const,
  detail: (id: string) => [...productsKeys.details(), id] as const,
};

export const categoriesKeys = {
  all: ["categories"] as const,
};

// ─── Product Queries ─────────────────────────────────────────────────────

export function useProducts(filters?: { active?: boolean }) {
  return useQuery({
    queryKey: productsKeys.list(filters),
    queryFn: async () => {
      const params: Record<string, string> = {};
      if (filters?.active !== undefined) {
        params.active = String(filters.active);
      }
      const { data } = await apiClient.get<Product[]>(
        "/api/v1/products",
        { params }
      );
      return data;
    },
  });
}

export function useProduct(id: string) {
  return useQuery({
    queryKey: productsKeys.detail(id),
    queryFn: async () => {
      const { data } = await apiClient.get<Product>(
        `/api/v1/products/${id}`
      );
      return data;
    },
    enabled: !!id,
  });
}

// ─── Product Mutations ────────────────────────────────────────────────────

export interface CreateProductInput {
  name: string;
  genericName?: string;
  categoryId?: string;
  concentration?: string;
  dosageForm?: string;
  manufacturer?: string;
  requiresPrescription?: boolean;
  isControlled?: boolean;
  storageCondition?: string;
  unit: string;
  imageUrl?: string;
  salePrice?: number;
  purchasePrice?: number;
  expirationDate: string;
  manufacturingDate: string;
}

export type UpdateProductInput = CreateProductInput;

export function useCreateProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: CreateProductInput) => {
      const { data } = await apiClient.post<Product>(
        "/api/v1/products",
        input
      );
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productsKeys.lists() });
    },
  });
}

export function useUpdateProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      id,
      data,
    }: {
      id: string;
      data: UpdateProductInput;
    }) => {
      const response = await apiClient.put<Product>(
        `/api/v1/products/${id}`,
        data
      );
      return response.data;
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: productsKeys.lists() });
      queryClient.invalidateQueries({
        queryKey: productsKeys.detail(data.id),
      });
    },
  });
}

export function useDeactivateProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      await apiClient.delete(`/api/v1/products/${id}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productsKeys.lists() });
    },
  });
}

export function useReactivateProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      const { data } = await apiClient.patch<Product>(
        `/api/v1/products/${id}/reactivate`
      );
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productsKeys.lists() });
    },
  });
}

// ─── Category Queries ─────────────────────────────────────────────────────

export function useCategories() {
  return useQuery({
    queryKey: categoriesKeys.all,
    queryFn: async () => {
      const { data } = await apiClient.get<Category[]>("/api/v1/categories");
      return data;
    },
  });
}

export function useCreateCategory() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: { name: string; description?: string }) => {
      const { data } = await apiClient.post<Category>(
        "/api/v1/categories",
        input
      );
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: categoriesKeys.all });
    },
  });
}
