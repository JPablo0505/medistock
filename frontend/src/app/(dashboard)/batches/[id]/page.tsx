"use client";

import { use, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useBatch, useQuarantineBatch, useReleaseFromQuarantine, useDisposeBatch } from "@/hooks/use-batches";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  AlertTriangle,
  ArrowLeft,
  Archive,
  Ban,
  CheckCircle2,
  ShieldAlert,
  Undo2,
} from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";

const statusConfig: Record<
  string,
  { label: string; variant: "default" | "destructive" | "secondary" | "outline" }
> = {
  ACTIVE: { label: "Activo", variant: "default" },
  QUARANTINED: { label: "En cuarentena", variant: "destructive" },
  EXPIRED: { label: "Vencido", variant: "destructive" },
  DEPLETED: { label: "Agotado", variant: "secondary" },
  DISPOSED: { label: "Desechado", variant: "outline" },
};

function statusBadge(status: string) {
  const cfg = statusConfig[status] ?? { label: status, variant: "secondary" as const };
  return <Badge variant={cfg.variant}>{cfg.label}</Badge>;
}

export default function BatchDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const router = useRouter();
  const { data, isLoading, error } = useBatch(id);
  const quarantineMutation = useQuarantineBatch();
  const releaseMutation = useReleaseFromQuarantine();
  const disposeMutation = useDisposeBatch();

  const [reason, setReason] = useState("");
  const [dialogOpen, setDialogOpen] = useState(false);
  const [currentAction, setCurrentAction] = useState<string | null>(null);

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-32 w-full rounded-lg" />
        <Skeleton className="h-48 w-full rounded-lg" />
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="flex flex-col items-center gap-2 py-12 text-center">
        <AlertTriangle className="h-8 w-8 text-destructive" />
        <p className="text-sm text-muted-foreground">Lote no encontrado</p>
        <Button asChild variant="outline" size="sm">
          <Link href="/batches">Volver a lotes</Link>
        </Button>
      </div>
    );
  }

  const { batch, movements } = data;

  async function handleAction(action: string) {
    setCurrentAction(action);
    try {
      switch (action) {
        case "quarantine":
          await quarantineMutation.mutateAsync({ id, reason });
          break;
        case "release":
          await releaseMutation.mutateAsync({ id, reason });
          break;
        case "dispose":
          await disposeMutation.mutateAsync({ id, reason });
          break;
      }
      setDialogOpen(false);
      setReason("");
    } catch {
      // error handled by mutation state
    }
  }

  const needsReason = ["quarantine", "release", "dispose"];
  const actions = [];

  if (batch.status === "ACTIVE") {
    actions.push(
      { key: "quarantine", label: "Poner en cuarentena", icon: ShieldAlert, variant: "destructive" as const },
      { key: "dispose", label: "Desechar lote", icon: Archive, variant: "destructive" as const }
    );
  }
  if (batch.status === "QUARANTINED") {
    actions.push(
      { key: "release", label: "Liberar de cuarentena", icon: Undo2, variant: "outline" as const },
      { key: "dispose", label: "Desechar lote", icon: Archive, variant: "destructive" as const }
    );
  }

  return (
    <div className="space-y-6">
      {/* Back */}
      <Button asChild variant="ghost" size="sm">
        <Link href="/batches">
          <ArrowLeft className="mr-2 h-4 w-4" />
          Volver a lotes
        </Link>
      </Button>

      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight">
              {batch.batchCode}
            </h1>
            {statusBadge(batch.status)}
          </div>
          <p className="mt-1 text-sm text-muted-foreground">
            Producto: {batch.productId.slice(0, 8)}...
          </p>
        </div>

        {/* Actions */}
        <div className="flex gap-2">
          {actions.map((action) => (
            <Dialog
              key={action.key}
              open={dialogOpen && currentAction === action.key}
              onOpenChange={(open) => {
                setDialogOpen(open);
                if (open) setCurrentAction(action.key);
              }}
            >
              <DialogTrigger asChild>
                <Button variant={action.variant} size="sm">
                  <action.icon className="mr-2 h-4 w-4" />
                  {action.label}
                </Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>{action.label}</DialogTitle>
                  <DialogDescription>
                    Ingresá el motivo para esta acción
                  </DialogDescription>
                </DialogHeader>
                <div className="space-y-4 py-2">
                  <Textarea
                    placeholder="Motivo..."
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                  />
                </div>
                <DialogFooter>
                  <Button
                    variant="outline"
                    onClick={() => setDialogOpen(false)}
                  >
                    Cancelar
                  </Button>
                  <Button
                    variant={action.variant === "destructive" ? "destructive" : "default"}
                    onClick={() => handleAction(action.key)}
                    disabled={needsReason.includes(action.key) && !reason.trim()}
                  >
                    Confirmar
                  </Button>
                </DialogFooter>
              </DialogContent>
            </Dialog>
          ))}
        </div>
      </div>

      {/* Details */}
      <div className="grid gap-4 md:grid-cols-2">
        <div className="rounded-lg border p-4">
          <h3 className="mb-3 text-sm font-medium text-muted-foreground">
            Información del lote
          </h3>
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between">
              <dt className="text-muted-foreground">Código</dt>
              <dd className="font-medium">{batch.batchCode}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-muted-foreground">Cantidad inicial</dt>
              <dd className="font-medium">{batch.initialQuantity}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-muted-foreground">Cantidad actual</dt>
              <dd className="font-medium">{batch.currentQuantity}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-muted-foreground">Vencimiento</dt>
              <dd className="font-medium">
                {new Date(batch.expirationDate).toLocaleDateString("es-AR")}
              </dd>
            </div>
            {batch.manufacturingDate && (
              <div className="flex justify-between">
                <dt className="text-muted-foreground">Fabricación</dt>
                <dd className="font-medium">
                  {new Date(batch.manufacturingDate).toLocaleDateString("es-AR")}
                </dd>
              </div>
            )}
            <div className="flex justify-between">
              <dt className="text-muted-foreground">Costo unitario</dt>
              <dd className="font-medium">${Number(batch.costPerUnit).toFixed(2)}</dd>
            </div>
            {batch.category && (
              <div className="flex justify-between">
                <dt className="text-muted-foreground">Categoría</dt>
                <dd className="font-medium">{batch.category}</dd>
              </div>
            )}
            {batch.notes && (
              <div className="flex justify-between">
                <dt className="text-muted-foreground">Notas</dt>
                <dd className="font-medium">{batch.notes}</dd>
              </div>
            )}
          </dl>
        </div>

        {/* Status actions */}
        <div className="rounded-lg border p-4">
          <h3 className="mb-3 text-sm font-medium text-muted-foreground">
            Acciones disponibles
          </h3>
          <div className="space-y-2 text-sm text-muted-foreground">
            {batch.status === "ACTIVE" && (
              <p>El lote está activo y disponible para asignación FEFO.</p>
            )}
            {batch.status === "QUARANTINED" && (
              <p>
                El lote está en cuarentena. No se puede asignar hasta que sea
                liberado.
              </p>
            )}
            {batch.status === "DEPLETED" && (
              <p>
                El lote está agotado (stock en cero). No se pueden realizar más
                movimientos.
              </p>
            )}
            {batch.status === "DISPOSED" && (
              <p>El lote fue desechado. No se pueden realizar más acciones.</p>
            )}
            {batch.status === "EXPIRED" && (
              <p>
                El lote está vencido. Debe ser desechado.
              </p>
            )}
          </div>
        </div>
      </div>

      {/* Movements Timeline */}
      <div className="rounded-lg border">
        <div className="border-b px-4 py-3">
          <h3 className="font-medium">Movimientos</h3>
        </div>
        {movements.length === 0 ? (
          <div className="flex flex-col items-center gap-2 py-8 text-center">
            <p className="text-sm text-muted-foreground">
              Sin movimientos registrados
            </p>
          </div>
        ) : (
          <div className="divide-y">
            {movements.map((mov) => (
              <div
                key={mov.id}
                className="flex items-center justify-between px-4 py-3"
              >
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium">
                      {movementLabel(mov.type)}
                    </span>
                    <span
                      className={`text-sm font-semibold ${
                        mov.quantity > 0
                          ? "text-green-600"
                          : mov.quantity < 0
                            ? "text-red-600"
                            : "text-muted-foreground"
                      }`}
                    >
                      {mov.quantity > 0 ? "+" : ""}
                      {mov.quantity}
                    </span>
                  </div>
                  {mov.reason && (
                    <p className="text-xs text-muted-foreground">
                      {mov.reason}
                    </p>
                  )}
                </div>
                <div className="text-right text-xs text-muted-foreground">
                  <p>{new Date(mov.createdAt).toLocaleDateString("es-AR")}</p>
                  <p>{new Date(mov.createdAt).toLocaleTimeString("es-AR", { hour: "2-digit", minute: "2-digit" })}</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function movementLabel(type: string): string {
  const labels: Record<string, string> = {
    RECEIPT: "Recepción",
    ADJUSTMENT: "Ajuste",
    WRITE_OFF: "Baja",
    TRANSFER: "Transferencia",
    ALLOCATED: "Asignado",
    QUARANTINE_IN: "Cuarentena",
    QUARANTINE_OUT: "Liberado de cuarentena",
  };
  return labels[type] ?? type;
}
