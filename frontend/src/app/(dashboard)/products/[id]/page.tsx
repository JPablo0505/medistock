"use client";

import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  useProduct,
  useDeactivateProduct,
  useReactivateProduct,
  useCategories,
} from "@/hooks/use-products";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  ArrowLeft,
  Edit,
  AlertTriangle,
  RefreshCw,
  Package,
} from "lucide-react";
import { formatCOP } from "@/lib/format";

export default function ProductDetailPage() {
  const params = useParams();
  const router = useRouter();
  const id = params.id as string;
  const { data: product, isLoading, error, refetch } = useProduct(id);
  const { data: categories } = useCategories();
  const deactivate = useDeactivateProduct();
  const reactivate = useReactivateProduct();

  const categoryName = categories?.find(
    (c) => c.id === product?.categoryId
  )?.name;

  // ─── Loading State ────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-48" />
        <Card>
          <CardHeader>
            <Skeleton className="h-6 w-64" />
            <Skeleton className="h-4 w-32" />
          </CardHeader>
          <CardContent className="space-y-4">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-5 w-full" />
            ))}
          </CardContent>
        </Card>
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
          <Button variant="outline" onClick={() => router.push("/products")}>
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to Products
          </Button>
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
        <Package className="h-12 w-12 text-muted-foreground/50" />
        <p className="mt-4 text-lg font-medium">Product not found</p>
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

  // ─── Action Handlers ──────────────────────────────────────────────────

  const handleDeactivate = async () => {
    await deactivate.mutateAsync(id);
    refetch();
  };

  const handleReactivate = async () => {
    await reactivate.mutateAsync(id);
    refetch();
  };

  // ─── Detail View ──────────────────────────────────────────────────────

  const detailRows = [
    { label: "Generic Name", value: product.genericName },
    { label: "Category", value: categoryName ?? product.categoryId },
    { label: "Concentration", value: product.concentration },
    { label: "Dosage Form", value: product.dosageForm },
    { label: "Manufacturer", value: product.manufacturer },
    { label: "Unit", value: product.unit },
    { label: "Storage Condition", value: product.storageCondition },
    {
      label: "Requires Prescription",
      value: product.requiresPrescription ? "Yes" : "No",
    },
    {
      label: "Controlled Substance",
      value: product.isControlled ? "Yes" : "No",
    },
    {
      label: "Sale Price",
      value: formatCOP(product.salePrice),
    },
    {
      label: "Purchase Price",
      value: formatCOP(product.purchasePrice),
    },
    {
      label: "Stock",
      value: `${product.stockQuantity ?? 0} unidades`,
    },
    { label: "Expiration Date", value: product.expirationDate },
    { label: "Manufacturing Date", value: product.manufacturingDate },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="icon"
            onClick={() => router.push("/products")}
          >
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-2xl font-bold tracking-tight">
                {product.name}
              </h1>
              <Badge
                variant={product.isActive ? "default" : "secondary"}
              >
                {product.isActive ? "Active" : "Inactive"}
              </Badge>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <Link href={`/products/${product.id}/edit`}>
            <Button variant="outline">
              <Edit className="mr-2 h-4 w-4" />
              Edit
            </Button>
          </Link>
          {product.isActive ? (
            <Button
              variant="destructive"
              onClick={handleDeactivate}
              disabled={deactivate.isPending}
            >
              {deactivate.isPending ? "Deactivating..." : "Deactivate"}
            </Button>
          ) : (
            <Button
              variant="outline"
              onClick={handleReactivate}
              disabled={reactivate.isPending}
            >
              {reactivate.isPending ? "Reactivating..." : "Reactivate"}
            </Button>
          )}
        </div>
      </div>

      {/* Deactivated Banner */}
      {!product.isActive && (
        <div className="flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          <AlertTriangle className="h-4 w-4" />
          This product is deactivated. It will not appear in active product
          listings.
        </div>
      )}

      {/* Product Image */}
      {product.imageUrl && (
        <div className="overflow-hidden rounded-xl border">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={product.imageUrl}
            alt={product.name}
            className="max-h-96 w-full object-contain bg-muted"
            onError={(e) => {
              (e.target as HTMLImageElement).style.display = "none";
            }}
          />
        </div>
      )}

      {/* Details Card */}
      <Card>
        <CardHeader>
          <CardTitle>Product Details</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 gap-x-8 gap-y-3 sm:grid-cols-2">
            {detailRows.map(
              (row) =>
                row.value && (
                  <div
                    key={row.label}
                    className="flex justify-between border-b pb-2 last:border-b-0"
                  >
                    <span className="text-sm text-muted-foreground">
                      {row.label}
                    </span>
                    <span className="text-sm font-medium">{row.value}</span>
                  </div>
                )
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
