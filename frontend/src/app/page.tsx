"use client";

import { useDashboardSummary } from "@/hooks/use-dashboard";
import { useAlertInstances } from "@/hooks/use-alerts";
import { useBatches } from "@/hooks/use-batches";
import { useProducts } from "@/hooks/use-products";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { StatCardSkeleton } from "@/components/ui/loading-skeletons";
import {
  Package,
  AlertTriangle,
  Clock,
  PillBottle,
  FlaskConical,
  TrendingUp,
  TrendingDown,
  Minus,
  CheckCircle,
  XCircle,
  AlertCircle,
  Activity,
} from "lucide-react";
import { formatCOP } from "@/lib/format";

const severityConfig: Record<string, { icon: typeof AlertTriangle; color: string; bg: string }> = {
  CRITICAL: { icon: XCircle, color: "text-red-600", bg: "bg-red-50" },
  HIGH: { icon: AlertCircle, color: "text-orange-600", bg: "bg-orange-50" },
  MEDIUM: { icon: AlertTriangle, color: "text-amber-600", bg: "bg-amber-50" },
  LOW: { icon: CheckCircle, color: "text-blue-600", bg: "bg-blue-50" },
};

function daysUntil(dateStr: string): number {
  const now = new Date();
  const exp = new Date(dateStr);
  const diff = Math.ceil((exp.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
  return diff;
}

export default function DashboardPage() {
  const { data, isLoading } = useDashboardSummary();
  const { data: alerts } = useAlertInstances();
  const { data: batches } = useBatches();
  const { data: products } = useProducts();

  // ─── Loading State ────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Dashboard</h1>
          <p className="text-sm text-muted-foreground">
            Overview of your pharmaceutical inventory
          </p>
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
          {Array.from({ length: 5 }).map((_, i) => (
            <StatCardSkeleton key={i} />
          ))}
        </div>
      </div>
    );
  }

  // ─── Compute data from real sources ───────────────────────────────────
  const totalProducts = data?.totalProducts ?? 0;
  const activeProductsCount = products?.filter((p) => p.isActive).length ?? 0;
  const activeBatchesCount = data?.activeBatches ?? 0;
  const activeAlertsCount = data?.activeAlerts ?? 0;
  const expiringSoonCount = data?.expiringSoonBatches ?? 0;
  const inactiveProducts = totalProducts - activeProductsCount;

  const statCards = [
    {
      key: "totalProducts",
      label: "Total Products",
      icon: Package,
      color: "text-blue-600",
      bg: "bg-blue-100",
      border: "border-l-blue-500",
      value: totalProducts,
      trend: inactiveProducts > 0 ? `${inactiveProducts} inactivos` : "Todos activos",
      trendUp: inactiveProducts === 0 ? true : inactiveProducts <= 1 ? null : false,
    },
    {
      key: "activeBatches",
      label: "Lotes Activos",
      icon: FlaskConical,
      color: "text-indigo-600",
      bg: "bg-indigo-100",
      border: "border-l-indigo-500",
      value: activeBatchesCount,
      trend: activeBatchesCount > 0 ? "Con stock" : "Sin lotes",
      trendUp: activeBatchesCount > 0 ? true : null,
    },
    {
      key: "activeAlerts",
      label: "Alertas Activas",
      icon: AlertTriangle,
      color: "text-red-600",
      bg: "bg-red-100",
      border: "border-l-red-500",
      value: activeAlertsCount,
      trend: activeAlertsCount === 0 ? "Todo OK" : `${activeAlertsCount} pendientes`,
      trendUp: activeAlertsCount === 0 ? true : false,
    },
    {
      key: "expiringSoonBatches",
      label: "Vencen Pronto (30d)",
      icon: Clock,
      color: "text-amber-600",
      bg: "bg-amber-100",
      border: "border-l-amber-500",
      value: expiringSoonCount,
      trend: expiringSoonCount === 0 ? "Ninguno" : `${expiringSoonCount} lotes`,
      trendUp: expiringSoonCount === 0 ? true : false,
    },
    {
      key: "activeProducts",
      label: "Productos Activos",
      icon: PillBottle,
      color: "text-green-600",
      bg: "bg-green-100",
      border: "border-l-green-500",
      value: activeProductsCount,
      trend: totalProducts > 0 ? `${Math.round((activeProductsCount / totalProducts) * 100)}% del total` : "Sin datos",
      trendUp: activeProductsCount === totalProducts ? true : activeProductsCount > 0 ? null : false,
    },
  ];

  // Alerts
  const activeAlerts = alerts?.filter((a) => a.status === "ACTIVE") ?? [];

  // Group expiring batches by product
  const expiringByProduct: { productName: string; batches: NonNullable<typeof batches> }[] = [];
  const expiringMap = new Map<string, number>();
  batches
    ?.filter((b) => {
      if (!b.expirationDate || b.status !== "ACTIVE") return false;
      return daysUntil(b.expirationDate) <= 30 && daysUntil(b.expirationDate) > 0;
    })
    .sort((a, b) => new Date(a.expirationDate!).getTime() - new Date(b.expirationDate!).getTime())
    .forEach((batch) => {
      const key = batch.productId;
      const idx = expiringMap.get(key);
      if (idx !== undefined) {
        expiringByProduct[idx].batches.push(batch);
      } else {
        expiringMap.set(key, expiringByProduct.length);
        expiringByProduct.push({ productName: batch.productName ?? "Producto", batches: [batch] });
      }
    });

  // Out of stock products
  const outOfStockProducts =
    products
      ?.filter((p) => {
        if (!p.isActive) return false;
        const totalStock =
          batches
            ?.filter((b) => b.productId === p.id && b.status === "ACTIVE")
            .reduce((sum, b) => sum + b.currentQuantity, 0) ?? 0;
        return totalStock === 0;
      })
      .slice(0, 5) ?? [];

  // Recent activity
  const recentBatches =
    batches
      ?.filter((b) => b.status === "ACTIVE")
      .sort((a, b) => (b.expirationDate ?? "").localeCompare(a.expirationDate ?? ""))
      .slice(0, 5) ?? [];

  // ─── Render ───────────────────────────────────────────────────────────
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Dashboard</h1>
        <p className="text-sm text-muted-foreground">
          Overview of your pharmaceutical inventory
        </p>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
        {statCards.map((card) => {
          const Icon = card.icon;
          const TrendIcon =
            card.trendUp === true ? TrendingUp : card.trendUp === false ? TrendingDown : Minus;

          return (
            <Card key={card.key} className={`border-l-4 ${card.border}`}>
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-sm font-medium text-muted-foreground">
                  {card.label}
                </CardTitle>
                <div className={`rounded-lg p-2 ${card.bg}`}>
                  <Icon className={`h-4 w-4 ${card.color}`} />
                </div>
              </CardHeader>
              <CardContent>
                <p className="text-3xl font-bold">{card.value}</p>
                <div className="mt-1 flex items-center gap-1 text-xs">
                  <TrendIcon
                    className={`h-3 w-3 ${
                      card.trendUp === true
                        ? "text-green-600"
                        : card.trendUp === false
                          ? "text-red-600"
                          : "text-muted-foreground"
                    }`}
                  />
                  <span
                    className={
                      card.trendUp === true
                        ? "text-green-600 font-medium"
                        : card.trendUp === false
                          ? "text-red-600 font-medium"
                          : "text-muted-foreground"
                    }
                  >
                    {card.trend}
                  </span>
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      {/* Main Grid: Content + Sidebar */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Left Column: Recent Activity */}
        <div className="lg:col-span-2 space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Activity className="h-5 w-5 text-primary" />
                Actividad Reciente
              </CardTitle>
            </CardHeader>
            <CardContent>
              {recentBatches.length === 0 ? (
                <p className="text-sm text-muted-foreground">No hay actividad reciente</p>
              ) : (
                <div className="space-y-4">
                  {recentBatches.map((batch) => (
                    <div key={batch.id} className="flex items-start gap-3">
                      <div className="mt-1 h-2 w-2 shrink-0 rounded-full bg-blue-500" />
                      <div className="flex-1 min-w-0">
                        <div className="flex justify-between">
                          <p className="text-sm font-medium truncate">
                            Lote recibido: {batch.batchCode}
                          </p>
                          <span className="text-xs text-muted-foreground whitespace-nowrap ml-2">
                            {batch.expirationDate
                              ? `Exp: ${batch.expirationDate}`
                              : ""}
                          </span>
                        </div>
                        <p className="text-xs text-muted-foreground">
                          {batch.initialQuantity} unidades - {formatCOP(batch.costPerUnit)}/unit
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        {/* Right Column: Alerts + Expiring + Out of Stock */}
        <div className="space-y-6">
          {/* Active Alerts */}
          <Card className="border-red-200 bg-red-50/50">
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-red-800">
                <AlertTriangle className="h-5 w-5" />
                Alertas Activas
              </CardTitle>
            </CardHeader>
            <CardContent>
              {activeAlerts.length === 0 ? (
                <p className="text-sm text-red-600/70">No hay alertas activas</p>
              ) : (
                <div className="space-y-3">
                  {activeAlerts.slice(0, 5).map((alert) => {
                    const cfg = severityConfig[alert.severity] ?? severityConfig.LOW;
                    const SevIcon = cfg.icon;
                    return (
                      <div
                        key={alert.id}
                        className={`flex items-start gap-2 rounded-lg border p-2 ${cfg.bg} border-transparent`}
                      >
                        <SevIcon className={`mt-0.5 h-4 w-4 shrink-0 ${cfg.color}`} />
                        <div className="min-w-0">
                          <p className="text-sm font-medium truncate">{alert.message}</p>
                          <p className="text-xs text-muted-foreground">
                            {alert.productName ?? "Producto"} - {alert.severity}
                          </p>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Expiring Soon - grouped by product */}
          <Card className="border-amber-200 bg-amber-50/50">
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-amber-800">
                <Clock className="h-5 w-5" />
                Proximos a Vencer
              </CardTitle>
            </CardHeader>
            <CardContent>
              {expiringByProduct.length === 0 ? (
                <p className="text-sm text-amber-600/70">No hay lotes proximos a vencer</p>
              ) : (
                <div className="space-y-4">
                  {expiringByProduct.slice(0, 5).map((group, idx) => (
                    <div key={idx} className="border-b border-amber-200/50 pb-3 last:border-b-0 last:pb-0">
                      <p className="text-sm font-semibold text-amber-900 mb-1">{group.productName}</p>
                      <div className="space-y-1.5">
                        {group.batches.map((batch) => {
                          const days = daysUntil(batch.expirationDate!);
                          return (
                            <div key={batch.id} className="flex items-center justify-between">
                              <span className="text-xs text-muted-foreground">
                                {batch.batchCode} · {batch.currentQuantity} unid.
                              </span>
                              <Badge
                                variant={days <= 7 ? "destructive" : "outline"}
                                className="shrink-0 ml-2 text-xs"
                              >
                                {days}d
                              </Badge>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Out of Stock - products */}
          <Card className="border-slate-200 bg-slate-50/50">
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-slate-800">
                <Package className="h-5 w-5" />
                Sin Stock
              </CardTitle>
            </CardHeader>
            <CardContent>
              {outOfStockProducts.length === 0 ? (
                <p className="text-sm text-slate-600/70">Todos los productos tienen stock</p>
              ) : (
                <div className="space-y-3">
                  {outOfStockProducts.map((product) => (
                    <div key={product.id} className="flex items-center gap-3">
                      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded bg-red-100">
                        <Package className="h-4 w-4 text-red-600" />
                      </div>
                      <div className="min-w-0">
                        <p className="text-sm font-medium truncate">{product.name}</p>
                        <p className="text-xs text-red-600">Reorden pendiente</p>
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
