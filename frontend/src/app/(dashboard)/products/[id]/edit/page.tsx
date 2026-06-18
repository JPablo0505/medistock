"use client";

import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  useProduct,
  useUpdateProduct,
} from "@/hooks/use-products";
import { ProductForm, type ProductFormData } from "@/components/ProductForm";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ArrowLeft, AlertTriangle, RefreshCw } from "lucide-react";

export default function EditProductPage() {
  const params = useParams();
  const router = useRouter();
  const id = params.id as string;
  const { data: product, isLoading, error, refetch } = useProduct(id);
  const updateProduct = useUpdateProduct();

  const handleSave = async (data: ProductFormData) => {
    await updateProduct.mutateAsync({
      id,
      data: {
        name: data.name,
        genericName: data.genericName || undefined,
        categoryId: data.categoryId || undefined,
        concentration: data.concentration || undefined,
        dosageForm: data.dosageForm || undefined,
        manufacturer: data.manufacturer || undefined,
        requiresPrescription: data.requiresPrescription,
        isControlled: data.isControlled,
        storageCondition: data.storageCondition || undefined,
        unit: data.unit,
        salePrice: data.salePrice || undefined,
        purchasePrice: data.purchasePrice || undefined,
      },
    });

    router.push(`/products/${id}`);
  };

  // ─── Loading State ────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-48" />
        <div className="space-y-4 rounded-xl border p-6">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-10 w-full" />
          ))}
        </div>
      </div>
    );
  }

  // ─── Error State ──────────────────────────────────────────────────────
  if (error) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <AlertTriangle className="h-12 w-12 text-destructive" />
        <p className="mt-4 text-lg font-medium text-destructive">
          Failed to load product
        </p>
        <p className="mt-1 text-sm text-muted-foreground">
          {(error as Error).message}
        </p>
        <div className="mt-6 flex gap-3">
          <Link href="/products">
            <Button variant="outline">
              <ArrowLeft className="mr-2 h-4 w-4" />
              Back to Products
            </Button>
          </Link>
          <Button variant="outline" onClick={() => refetch()}>
            <RefreshCw className="mr-2 h-4 w-4" />
            Retry
          </Button>
        </div>
      </div>
    );
  }

  // ─── Not Found State ──────────────────────────────────────────────────
  if (!product) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <p className="text-lg font-medium">Product not found</p>
        <p className="mt-1 text-sm text-muted-foreground">
          This product may have been removed or doesn&apos;t exist.
        </p>
        <Link href="/products">
          <Button variant="outline" className="mt-6">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to Products
          </Button>
        </Link>
      </div>
    );
  }

  // ─── Edit Form ────────────────────────────────────────────────────────
  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Link href={`/products/${id}`}>
          <Button variant="ghost" size="icon">
            <ArrowLeft className="h-4 w-4" />
          </Button>
        </Link>
        <h1 className="text-2xl font-bold tracking-tight">Edit Product</h1>
      </div>

      <ProductForm
        product={product}
        onSave={handleSave}
        isSaving={updateProduct.isPending}
      />
    </div>
  );
}
