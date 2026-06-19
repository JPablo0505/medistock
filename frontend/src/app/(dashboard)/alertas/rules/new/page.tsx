"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useCreateAlertRule } from "@/hooks/use-alerts";
import { toast } from "sonner";
import type { AlertRuleType } from "@/types";

export default function NewAlertRulePage() {
  const router = useRouter();
  const createRule = useCreateAlertRule();

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [type, setType] = useState<AlertRuleType>("EXPIRATION");
  const [expirationDays, setExpirationDays] = useState("30");
  const [minStock, setMinStock] = useState("10");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const config: Record<string, unknown> = {};
    if (type === "EXPIRATION") {
      config.expirationDays = parseInt(expirationDays) || 30;
    } else if (type === "LOW_STOCK" || type === "CONTROLLED_STOCK") {
      config.minStock = parseInt(minStock) || 10;
    }
    config.severity = "MEDIUM";

    try {
      await createRule.mutateAsync({
        name,
        description: description || undefined,
        type,
        config,
      });
      toast.success("Regla creada exitosamente");
      router.push("/alertas");
    } catch {
      toast.error("Error al crear regla");
    }
  };

  return (
    <div className="space-y-6">
      <h1 className="text-3xl font-bold">Nueva regla de alerta</h1>

      <form onSubmit={handleSubmit}>
        <Card>
          <CardHeader>
            <CardTitle>Configuración</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-1">
              <Label>Nombre</Label>
              <Input value={name} onChange={(e) => setName(e.target.value)} required />
            </div>

            <div className="space-y-1">
              <Label>Descripción</Label>
              <Input value={description} onChange={(e) => setDescription(e.target.value)} />
            </div>

            <div className="space-y-1">
              <Label>Tipo</Label>
              <Select value={type} onValueChange={(v) => setType(v as AlertRuleType)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="EXPIRATION">Vencimiento</SelectItem>
                  <SelectItem value="LOW_STOCK">Stock bajo</SelectItem>
                  <SelectItem value="CONTROLLED_STOCK">Controlados</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {type === "EXPIRATION" && (
              <div className="space-y-1">
                <Label>Días antes del vencimiento</Label>
                <Input
                  type="number"
                  min={1}
                  value={expirationDays}
                  onChange={(e) => setExpirationDays(e.target.value)}
                />
              </div>
            )}

            {(type === "LOW_STOCK" || type === "CONTROLLED_STOCK") && (
              <div className="space-y-1">
                <Label>Stock mínimo</Label>
                <Input
                  type="number"
                  min={1}
                  value={minStock}
                  onChange={(e) => setMinStock(e.target.value)}
                />
              </div>
            )}
          </CardContent>
        </Card>

        <div className="mt-6 flex justify-end gap-3">
          <Button type="button" variant="outline" onClick={() => router.push("/alertas")}>
            Cancelar
          </Button>
          <Button type="submit" disabled={createRule.isPending || !name}>
            {createRule.isPending ? "Creando..." : "Crear regla"}
          </Button>
        </div>
      </form>
    </div>
  );
}
