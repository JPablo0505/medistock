"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CheckCircle, Eye, RefreshCw, AlertTriangle } from "lucide-react";
import type { AlertInstance, AlertInstanceStatus, AlertSeverity } from "@/types";

interface AlertInstanceListProps {
  instances: AlertInstance[] | undefined;
  isLoading: boolean;
  error: Error | null;
  onAcknowledge: (id: string) => void;
  onResolve: (id: string) => void;
  onEvaluate: () => void;
  isEvaluating: boolean;
}

const severityColors: Record<AlertSeverity, string> = {
  LOW: "bg-blue-100 text-blue-800",
  MEDIUM: "bg-yellow-100 text-yellow-800",
  HIGH: "bg-orange-100 text-orange-800",
  CRITICAL: "bg-red-100 text-red-800",
};

const statusLabels: Record<AlertInstanceStatus, string> = {
  ACTIVE: "Activa",
  ACKNOWLEDGED: "Reconocida",
  RESOLVED: "Resuelta",
};

export function AlertInstanceList({
  instances,
  isLoading,
  error,
  onAcknowledge,
  onResolve,
  onEvaluate,
  isEvaluating,
}: AlertInstanceListProps) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>Alertas activas</CardTitle>
        <Button variant="outline" size="sm" onClick={onEvaluate} disabled={isEvaluating}>
          <RefreshCw className={`mr-2 h-4 w-4 ${isEvaluating ? "animate-spin" : ""}`} />
          Evaluar ahora
        </Button>
      </CardHeader>
      <CardContent>
        {isLoading && (
          <div className="space-y-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-16 w-full" />
            ))}
          </div>
        )}

        {error && (
          <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-red-800">
            <p>Error al cargar alertas.</p>
          </div>
        )}

        {!isLoading && !error && (!instances || instances.length === 0) && (
          <div className="flex flex-col items-center gap-2 py-12 text-muted-foreground">
            <AlertTriangle className="h-8 w-8" />
            <p>No hay alertas activas</p>
          </div>
        )}

        {instances && instances.length > 0 && (
          <div className="space-y-3">
            {instances.map((instance) => (
              <div
                key={instance.id}
                className="flex items-start justify-between rounded-lg border p-4"
              >
                <div className="flex-1 space-y-1">
                  <div className="flex items-center gap-2">
                    <Badge className={severityColors[instance.severity]}>
                      {instance.severity}
                    </Badge>
                    <Badge variant="outline">{statusLabels[instance.status]}</Badge>
                  </div>
                  <p className="text-sm">{instance.message}</p>
                  <p className="text-xs text-muted-foreground">
                    {new Date(instance.createdAt).toLocaleString()}
                  </p>
                </div>
                <div className="flex gap-2">
                  {instance.status === "ACTIVE" && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => onAcknowledge(instance.id)}
                    >
                      <Eye className="mr-1 h-3 w-3" />
                      Reconocer
                    </Button>
                  )}
                  {instance.status !== "RESOLVED" && (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => onResolve(instance.id)}
                    >
                      <CheckCircle className="mr-1 h-3 w-3" />
                      Resolver
                    </Button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
