"use client";

import { useAlertInstances, useAcknowledgeAlert, useResolveAlert, useEvaluateAlerts } from "@/hooks/use-alerts";
import { AlertInstanceList } from "@/components/alerts/alert-instance-list";
import { AlertRuleList } from "@/components/alerts/alert-rule-list";
import { useAlertRules, useToggleAlertRule, useDeleteAlertRule } from "@/hooks/use-alerts";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import Link from "next/link";
import { Plus } from "lucide-react";

export default function AlertsPage() {
  const { data: instances, isLoading: loadingInstances, error: instancesError } = useAlertInstances("ACTIVE");
  const { data: rules, isLoading: loadingRules, error: rulesError } = useAlertRules();
  const acknowledge = useAcknowledgeAlert();
  const resolve = useResolveAlert();
  const evaluate = useEvaluateAlerts();
  const toggle = useToggleAlertRule();
  const del = useDeleteAlertRule();

  const handleAcknowledge = async (id: string) => {
    try {
      await acknowledge.mutateAsync(id);
      toast.success("Alerta reconocida");
    } catch { toast.error("Error al reconocer alerta"); }
  };

  const handleResolve = async (id: string) => {
    try {
      await resolve.mutateAsync(id);
      toast.success("Alerta resuelta");
    } catch { toast.error("Error al resolver alerta"); }
  };

  const handleEvaluate = async () => {
    try {
      const count = await evaluate.mutateAsync();
      toast.success(`Evaluación completada: ${count} alertas nuevas`);
    } catch { toast.error("Error al evaluar alertas"); }
  };

  const handleToggle = async (id: string) => {
    try {
      await toggle.mutateAsync(id);
    } catch { toast.error("Error al cambiar estado de regla"); }
  };

  const handleDelete = async (id: string) => {
    try {
      await del.mutateAsync(id);
      toast.success("Regla eliminada");
    } catch { toast.error("Error al eliminar regla"); }
  };

  return (
    <div className="space-y-8">
      <h1 className="text-3xl font-bold">Alertas</h1>

      <AlertInstanceList
        instances={instances}
        isLoading={loadingInstances}
        error={instancesError as Error | null}
        onAcknowledge={handleAcknowledge}
        onResolve={handleResolve}
        onEvaluate={handleEvaluate}
        isEvaluating={evaluate.isPending}
      />

      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-semibold">Reglas de alerta</h2>
          <Button asChild>
            <Link href="/alertas/rules/new">
              <Plus className="mr-2 h-4 w-4" />
              Nueva regla
            </Link>
          </Button>
        </div>
        <AlertRuleList
          rules={rules}
          isLoading={loadingRules}
          error={rulesError as Error | null}
          onToggle={handleToggle}
          onDelete={handleDelete}
        />
      </div>
    </div>
  );
}
