"use client";

import { useState } from "react";
import Link from "next/link";
import { useBatches } from "@/hooks/use-batches";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Package,
  Plus,
  Search,
  AlertTriangle,
  Ban,
  CheckCircle2,
} from "lucide-react";

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

function StatusBadge({ status }: { status: string }) {
  const cfg = statusConfig[status] ?? {
    label: status,
    variant: "secondary" as const,
  };
  return <Badge variant={cfg.variant}>{cfg.label}</Badge>;
}

export default function BatchesPage() {
  const { data: batches, isLoading, error } = useBatches();
  const [search, setSearch] = useState("");

  const filtered = (batches ?? []).filter(
    (b) =>
      b.batchCode.toLowerCase().includes(search.toLowerCase()) ||
      b.productId.includes(search)
  );

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">
            Lotes de inventario
          </h1>
          <p className="text-sm text-muted-foreground">
            Gestioná los lotes de productos farmacéuticos
          </p>
        </div>
        <Button asChild>
          <Link href="/batches/new">
            <Plus className="mr-2 h-4 w-4" />
            Nuevo lote
          </Link>
        </Button>
      </div>

      {/* Search */}
      <div className="relative">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          placeholder="Buscar por código de lote o producto..."
          className="pl-9"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      {/* Content */}
      {isLoading && (
        <div className="space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-20 w-full rounded-lg" />
          ))}
        </div>
      )}

      {error && (
        <div className="flex flex-col items-center gap-2 py-12 text-center">
          <AlertTriangle className="h-8 w-8 text-destructive" />
          <p className="text-sm text-muted-foreground">
            Error al cargar los lotes
          </p>
        </div>
      )}

      {!isLoading && !error && filtered.length === 0 && (
        <div className="flex flex-col items-center gap-2 py-12 text-center">
          <Package className="h-8 w-8 text-muted-foreground" />
          <p className="text-sm text-muted-foreground">
            {search
              ? "No se encontraron lotes con ese criterio"
              : "Todavía no hay lotes registrados"}
          </p>
          {!search && (
            <Button asChild variant="outline" size="sm">
              <Link href="/batches/new">Recibir primer lote</Link>
            </Button>
          )}
        </div>
      )}

      {!isLoading && !error && filtered.length > 0 && (
        <div className="space-y-3">
          {filtered.map((batch) => (
            <Link
              key={batch.id}
              href={`/batches/${batch.id}`}
              className="flex items-center justify-between rounded-lg border p-4 transition-colors hover:bg-accent"
            >
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{batch.batchCode}</span>
                  <StatusBadge status={batch.status} />
                </div>
                <p className="mt-1 text-sm text-muted-foreground">
                  Producto: {batch.productName || batch.productId.slice(0, 8) + "..."} &middot;{" "}
                  {batch.currentQuantity} / {batch.initialQuantity}{" "}
                  {batch.currentQuantity === 1 ? "unidad" : "unidades"}
                </p>
                <p className="text-xs text-muted-foreground">
                  Vence:{" "}
                  {new Date(batch.expirationDate).toLocaleDateString("es-AR")}
                  &nbsp;&middot;&nbsp; Costo: $
                  {Number(batch.costPerUnit).toFixed(2)}
                </p>
              </div>
              <ChevronRightIcon className="h-4 w-4 text-muted-foreground" />
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}

function ChevronRightIcon({ className }: { className?: string }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
    >
      <path d="m9 18 6-6-6-6" />
    </svg>
  );
}
