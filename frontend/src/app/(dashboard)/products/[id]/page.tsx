"use client";

import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  useProduct,
  useDeactivateProduct,
  useReactivateProduct,
  useCategories,
} from "@/hooks/use-products";
import { useBatches } from "@/hooks/use-batches";
import { useSales } from "@/hooks/use-sales";
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
  Clock,
  Activity,
  TrendingUp,
  CheckCircle,
  XCircle,
  AlertCircle,
} from "lucide-react";
import { formatCOP } from "@/lib/format";

const batchStatusConfig: Record<
  string,
  { label: string; variant: "default" | "destructive" | "secondary" | "outline"; icon: typeof CheckCircle; color: string }
> = {
  ACTIVE: { label: "Activo", variant: "default", icon: CheckCircle, color: "text-green-600" },
  EXPIRED: { label: "Vencido", variant: "destructive", icon: XCircle, color: "text-red-600" },
  QUARANTINED: { label: "Cuarentena", variant: "destructive", icon: AlertCircle, color: "text-orange-600" },
  DEPLETED: { label: "Agotado", variant: "secondary", icon: Package, color: "text-slate-600" },
  DISPOSED: { label: "Desechado", variant: "outline", icon: XCircle, color: "text-slate-500" },
};

function daysUntil(dateStr: string): number {
  const now = new Date();
  const exp = new Date(dateStr);
  return Math.ceil((exp.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
}

export default function ProductDetailPage() {
  const params = useParams();
  const router = useRouter();
  const id = params.id as string;
  const { data: product, isLoading, error, refetch } = useProduct(id);
  const { data: categories } = useCategories();
  const { data: allBatches } = useBatches();
  const { data: productSales } = useSales({ productId: id, size: 100 });
  const deactivate = useDeactivateProduct();
  const reactivate = useReactivateProduct();

  const categoryName = categories?.find((c) => c.id === product?.categoryId)?.name;
  const productBatches = allBatches?.filter((b) => b.productId === id) ?? [];
  const sortedBatches = [...productBatches].sort(
    (a, b) => (b.expirationDate ?? "").localeCompare(a.expirationDate ?? "")
  );
  const activeBatches = productBatches.filter((b) => b.status === "ACTIVE");
  const totalStock = activeBatches.reduce((sum, b) => sum + b.currentQuantity, 0);

  // ─── Loading State ────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-48" />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-24 w-full" />
          ))}
        </div>
        <Skeleton className="h-64 w-full" />
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
    { label: "Requires Prescription", value: product.requiresPrescription ? "Yes" : "No" },
    { label: "Controlled Substance", value: product.isControlled ? "Yes" : "No" },
    { label: "Expiration Date", value: product.expirationDate },
    { label: "Manufacturing Date", value: product.manufacturingDate },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" onClick={() => router.push("/products")}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-2xl font-bold tracking-tight">{product.name}</h1>
              <Badge variant={product.isActive ? "default" : "secondary"}>
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
            <Button variant="destructive" onClick={handleDeactivate} disabled={deactivate.isPending}>
              {deactivate.isPending ? "Deactivating..." : "Deactivate"}
            </Button>
          ) : (
            <Button variant="outline" onClick={handleReactivate} disabled={reactivate.isPending}>
              {reactivate.isPending ? "Reactivating..." : "Reactivate"}
            </Button>
          )}
        </div>
      </div>

      {/* Deactivated Banner */}
      {!product.isActive && (
        <div className="flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          <AlertTriangle className="h-4 w-4" />
          This product is deactivated. It will not appear in active product listings.
        </div>
      )}

      {/* Hero Stats */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Card className="border-l-4 border-l-blue-500">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-muted-foreground">Total Stock</p>
                <p className="text-3xl font-bold">{totalStock}</p>
                <p className="text-xs text-muted-foreground">unidades en {activeBatches.length} lotes</p>
              </div>
              <Package className="h-8 w-8 text-blue-500" />
            </div>
          </CardContent>
        </Card>
        <Card className="border-l-4 border-l-green-500">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-muted-foreground">Sale Price</p>
                <p className="text-3xl font-bold">{formatCOP(product.salePrice)}</p>
              </div>
              <TrendingUp className="h-8 w-8 text-green-500" />
            </div>
          </CardContent>
        </Card>
        <Card className="border-l-4 border-l-indigo-500">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-muted-foreground">Purchase Price</p>
                <p className="text-3xl font-bold">{formatCOP(product.purchasePrice)}</p>
              </div>
              <Activity className="h-8 w-8 text-indigo-500" />
            </div>
          </CardContent>
        </Card>
      </div>

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

      {/* Main Grid */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Left Column: General Info */}
        <div className="lg:col-span-2 space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Package className="h-5 w-5 text-primary" />
                Informacion General
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-1 gap-x-8 gap-y-3 sm:grid-cols-2">
                {detailRows.map(
                  (row) =>
                    row.value && (
                      <div key={row.label} className="flex justify-between border-b pb-2 last:border-b-0">
                        <span className="text-sm text-muted-foreground">{row.label}</span>
                        <span className="text-sm font-medium">{row.value}</span>
                      </div>
                    )
                )}
              </div>
            </CardContent>
          </Card>

          {/* Sales History */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <TrendingUp className="h-5 w-5 text-primary" />
                Historial de Ventas
              </CardTitle>
            </CardHeader>
            <CardContent>
              {!productSales || productSales.length === 0 ? (
                <p className="text-sm text-muted-foreground">No hay ventas registradas para este producto</p>
              ) : (
                <div className="space-y-3">
                  {productSales.slice(0, 10).map((sale) => (
                    <div key={sale.id} className="flex items-center justify-between border-b pb-2 last:border-b-0">
                      <div className="min-w-0">
                        <p className="text-sm font-medium">{sale.saleDate}</p>
                        <p className="text-xs text-muted-foreground">{sale.itemCount} items - {sale.performedBy}</p>
                      </div>
                      <div className="text-right">
                        <p className="text-sm font-bold">{formatCOP(sale.total)}</p>
                        <Badge variant={sale.status === "COMPLETED" ? "default" : "secondary"} className="text-xs">
                          {sale.status}
                        </Badge>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        {/* Right Column: Batch History + Movements */}
        <div className="space-y-6">
          {/* Batch History */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Clock className="h-5 w-5 text-primary" />
                Historial de Lotes
              </CardTitle>
            </CardHeader>
            <CardContent>
              {sortedBatches.length === 0 ? (
                <p className="text-sm text-muted-foreground">No hay lotes registrados</p>
              ) : (
                <div className="space-y-4">
                  {sortedBatches.slice(0, 10).map((batch) => {
                    const cfg = batchStatusConfig[batch.status] ?? batchStatusConfig.ACTIVE;
                    const days = batch.expirationDate ? daysUntil(batch.expirationDate) : null;
                    return (
                      <div key={batch.id} className="flex items-start gap-3">
                        <div className={`mt-1 h-3 w-3 shrink-0 rounded-full ring-2 ring-background ${cfg.color === "text-green-600" ? "bg-green-500" : cfg.color === "text-red-600" ? "bg-red-500" : cfg.color === "text-orange-600" ? "bg-orange-500" : "bg-slate-400"}`} />
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center justify-between">
                            <p className="text-sm font-bold">{batch.batchCode}</p>
                            <Badge variant={cfg.variant} className="text-xs shrink-0 ml-2">
                              {cfg.label}
                            </Badge>
                          </div>
                          <p className="text-xs text-muted-foreground">
                            {batch.currentQuantity}/{batch.initialQuantity} unidades
                            {batch.expirationDate && ` - Exp: ${batch.expirationDate}`}
                          </p>
                          {days !== null && days <= 30 && days > 0 && (
                            <p className="text-xs text-amber-600 font-medium">
                              Vence en {days} dias
                            </p>
                          )}
                          {days !== null && days <= 0 && (
                            <p className="text-xs text-red-600 font-medium">Vencido</p>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Movements */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Activity className="h-5 w-5 text-primary" />
                Movimientos Recientes
              </CardTitle>
            </CardHeader>
            <CardContent>
              {productBatches.length === 0 && (!productSales || productSales.length === 0) ? (
                <p className="text-sm text-muted-foreground">No hay movimientos registrados</p>
              ) : (
                <div className="space-y-4">
                  {/* Batch receipts */}
                  {sortedBatches.slice(0, 3).map((batch) => (
                    <div key={`batch-${batch.id}`} className="flex items-start gap-3">
                      <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-blue-100">
                        <Package className="h-3.5 w-3.5 text-blue-600" />
                      </div>
                      <div>
                        <p className="text-sm">
                          <strong>Recepcion:</strong> +{batch.initialQuantity} unidades ({batch.batchCode})
                        </p>
                        <p className="text-xs text-muted-foreground">
                          Costo: {formatCOP(batch.costPerUnit)}/unit
                        </p>
                      </div>
                    </div>
                  ))}
                  {/* Recent sales */}
                  {productSales?.slice(0, 3).map((sale) => (
                    <div key={`sale-${sale.id}`} className="flex items-start gap-3">
                      <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-green-100">
                        <TrendingUp className="h-3.5 w-3.5 text-green-600" />
                      </div>
                      <div>
                        <p className="text-sm">
                          <strong>Venta:</strong> {sale.itemCount} items - {formatCOP(sale.total)}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          {sale.saleDate} - {sale.performedBy}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
