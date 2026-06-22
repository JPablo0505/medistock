"use client";

import { useDashboardSummary } from "@/hooks/use-dashboard";
import { useAlertInstances } from "@/hooks/use-alerts";
import { useBatches } from "@/hooks/use-batches";
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

const statCards = [
  {
    key: "totalProducts" as const,
    label: "Total Products",
    icon: Package,
    color: "text-blue-600",
    bg: "bg-blue-100",
    border: "border-l-blue-500",
    trend: "+2%",
    trendUp: true,
  },
  {
    key: "activeProducts" as const,
    label: "Active Products",
    icon: PillBottle,
    color: "text-green-600",
    bg: "bg-green-100",
    border: "border-l-green-500",
    trend: "Stable",
    trendUp: null,
  },
  {
    key: "activeBatches" as const,
    label: "Active Batches",
    icon: FlaskConical,
    color: "text-indigo-600",
    bg: "bg-indigo-100",
    border: "border-l-indigo-500",
    trend: "+5%",
    trendUp: true,
  },
  {
    key: "activeAlerts" as const,
    label: "Active Alerts",
    icon: AlertTriangle,
    color: "text-red-600",
    bg: "bg-red-100",
    border: "border-l-red-500",
    trend: "-1",
    trendUp: false,
  },
  {
    key: "expiringSoonBatches" as const,
    label: "Expiring Soon (30d)",
    icon: Clock,
    color: "text-amber-600",
    bg: "bg-amber-100",
    border: "border-l-amber-500",
    trend: "+3",
    trendUp: false,
  },
];

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

  const activeAlerts = alerts?.filter((a) => a.status === "ACTIVE") ?? [];
  const expiringBatches =
    batches
      ?.filter((b) => {
        if (!b.expirationDate || b.status !== "ACTIVE") return false;
        return daysUntil(b.expirationDate) <= 30 && daysUntil(b.expirationDate) > 0;
      })
      .sort((a, b) => new Date(a.expirationDate!).getTime() - new Date(b.expirationDate!).getTime())
      .slice(0, 5) ?? [];
  const outOfStockBatches =
    batches?.filter((b) => b.currentQuantity === 0 && b.status === "ACTIVE").slice(0, 5) ?? [];
  const recentBatches =
    batches
      ?.filter((b) => b.status === "ACTIVE")
      .sort((a, b) => (b.expirationDate ?? "").localeCompare(a.expirationDate ?? ""))
      .slice(0, 5) ?? [];

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
          const value = data?.[card.key];
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
                <p className="text-3xl font-bold">{value ?? "—"}</p>
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
          {/* Recent Activity */}
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

          {/* Expiring Soon */}
          <Card className="border-amber-200 bg-amber-50/50">
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-amber-800">
                <Clock className="h-5 w-5" />
                Proximos a Vencer
              </CardTitle>
            </CardHeader>
            <CardContent>
              {expiringBatches.length === 0 ? (
                <p className="text-sm text-amber-600/70">No hay lotes proximos a vencer</p>
              ) : (
                <div className="space-y-3">
                  {expiringBatches.map((batch) => {
                    const days = daysUntil(batch.expirationDate!);
                    return (
                      <div
                        key={batch.id}
                        className="flex items-center justify-between border-b border-amber-200/50 pb-2 last:border-b-0"
                      >
                        <div className="min-w-0">
                          <p className="text-sm font-medium truncate">{batch.batchCode}</p>
                          <p className="text-xs text-muted-foreground">
                            {batch.initialQuantity} unidades
                          </p>
                        </div>
                        <Badge
                          variant={days <= 7 ? "destructive" : "outline"}
                          className="shrink-0 ml-2"
                        >
                          {days}d
                        </Badge>
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Out of Stock */}
          <Card className="border-slate-200 bg-slate-50/50">
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-slate-800">
                <Package className="h-5 w-5" />
                Sin Stock
              </CardTitle>
            </CardHeader>
            <CardContent>
              {outOfStockBatches.length === 0 ? (
                <p className="text-sm text-slate-600/70">No hay lotes agotados</p>
              ) : (
                <div className="space-y-3">
                  {outOfStockBatches.map((batch) => (
                    <div key={batch.id} className="flex items-center gap-3">
                      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded bg-slate-200">
                        <Package className="h-4 w-4 text-slate-600" />
                      </div>
                      <div className="min-w-0">
                        <p className="text-sm font-medium truncate">{batch.batchCode}</p>
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
