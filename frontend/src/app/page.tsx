"use client";

import { useDashboardSummary } from "@/hooks/use-dashboard";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Package, AlertTriangle, Clock, PillBottle, FlaskConical } from "lucide-react";

const statCards = [
  {
    key: "totalProducts" as const,
    label: "Total Products",
    icon: Package,
    color: "text-blue-600",
    bg: "bg-blue-100",
  },
  {
    key: "activeProducts" as const,
    label: "Active Products",
    icon: PillBottle,
    color: "text-green-600",
    bg: "bg-green-100",
  },
  {
    key: "activeBatches" as const,
    label: "Active Batches",
    icon: FlaskConical,
    color: "text-indigo-600",
    bg: "bg-indigo-100",
  },
  {
    key: "activeAlerts" as const,
    label: "Active Alerts",
    icon: AlertTriangle,
    color: "text-red-600",
    bg: "bg-red-100",
  },
  {
    key: "expiringSoonBatches" as const,
    label: "Expiring Soon (30d)",
    icon: Clock,
    color: "text-amber-600",
    bg: "bg-amber-100",
  },
];

export default function DashboardPage() {
  const { data, isLoading } = useDashboardSummary();

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Dashboard</h1>
        <p className="text-sm text-muted-foreground">
          Overview of your pharmaceutical inventory
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
        {statCards.map((card) => {
          const Icon = card.icon;
          const value = data?.[card.key];

          return (
            <Card key={card.key}>
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-sm font-medium text-muted-foreground">
                  {card.label}
                </CardTitle>
                <div className={`rounded-lg p-2 ${card.bg}`}>
                  <Icon className={`h-4 w-4 ${card.color}`} />
                </div>
              </CardHeader>
              <CardContent>
                {isLoading ? (
                  <Skeleton className="h-8 w-16" />
                ) : (
                  <p className="text-3xl font-bold">{value ?? "—"}</p>
                )}
              </CardContent>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
