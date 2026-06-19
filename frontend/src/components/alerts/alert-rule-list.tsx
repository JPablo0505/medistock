"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Trash2, Plus, ToggleLeft, ToggleRight } from "lucide-react";
import Link from "next/link";
import type { AlertRule, AlertRuleType } from "@/types";

interface AlertRuleListProps {
  rules: AlertRule[] | undefined;
  isLoading: boolean;
  error: Error | null;
  onToggle: (id: string) => void;
  onDelete: (id: string) => void;
}

const typeLabels: Record<AlertRuleType, string> = {
  EXPIRATION: "Vencimiento",
  LOW_STOCK: "Stock bajo",
  CONTROLLED_STOCK: "Controlados",
  PRESCRIPTION: "Recetados",
};

export function AlertRuleList({ rules, isLoading, error, onToggle, onDelete }: AlertRuleListProps) {
  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-12 w-full" />
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-red-800">
        <p>Error al cargar reglas.</p>
      </div>
    );
  }

  if (!rules || rules.length === 0) {
    return (
      <div className="flex flex-col items-center gap-4 py-16">
        <p className="text-muted-foreground">No hay reglas de alerta configuradas</p>
        <Button asChild>
          <Link href="/alertas/rules/new">
            <Plus className="mr-2 h-4 w-4" />
            Crear regla
          </Link>
        </Button>
      </div>
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Nombre</TableHead>
          <TableHead>Tipo</TableHead>
          <TableHead>Activa</TableHead>
          <TableHead className="w-32">Acción</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rules.map((rule) => (
          <TableRow key={rule.id}>
            <TableCell>
              <Link href={`/alertas/rules/${rule.id}`} className="font-medium hover:underline">
                {rule.name}
              </Link>
              {rule.description && (
                <p className="text-xs text-muted-foreground">{rule.description}</p>
              )}
            </TableCell>
            <TableCell>
              <Badge variant="outline">{typeLabels[rule.type] || rule.type}</Badge>
            </TableCell>
            <TableCell>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => onToggle(rule.id)}
                className={rule.active ? "text-green-600" : "text-muted-foreground"}
              >
                {rule.active ? <ToggleRight className="h-5 w-5" /> : <ToggleLeft className="h-5 w-5" />}
                <span className="ml-1 text-xs">{rule.active ? "ON" : "OFF"}</span>
              </Button>
            </TableCell>
            <TableCell>
              <Button variant="ghost" size="icon" onClick={() => onDelete(rule.id)}>
                <Trash2 className="h-4 w-4" />
              </Button>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
