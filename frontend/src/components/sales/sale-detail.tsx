"use client";

import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { AlertTriangle, ArrowLeft, RefreshCw } from "lucide-react";
import Link from "next/link";
import type { SaleResponse, SaleStatus } from "@/types";

interface SaleDetailProps {
  sale: SaleResponse | undefined;
  isLoading: boolean;
  error: Error | null;
  onVoid?: (reason: string) => void;
  isVoiding?: boolean;
  onRetry?: () => void;
}

const statusLabels: Record<SaleStatus, string> = {
  COMPLETED: "Completada",
  VOIDED: "Anulada",
};

const statusColors: Record<SaleStatus, string> = {
  COMPLETED: "bg-green-100 text-green-800",
  VOIDED: "bg-gray-100 text-gray-800",
};

export function SaleDetail({ sale, isLoading, error, onVoid, isVoiding, onRetry }: SaleDetailProps) {
  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (error || !sale) {
    return (
      <div className="flex flex-col items-center gap-4 py-16">
        <p className="text-lg text-muted-foreground">Venta no encontrada</p>
        <div className="flex gap-2">
          <Button asChild variant="outline">
            <Link href="/ventas">
              <ArrowLeft className="mr-2 h-4 w-4" />
              Volver a ventas
            </Link>
          </Button>
          {onRetry && (
            <Button variant="outline" onClick={onRetry}>
              <RefreshCw className="mr-2 h-4 w-4" />
              Reintentar
            </Button>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Button asChild variant="ghost" size="icon">
            <Link href="/ventas">
              <ArrowLeft className="h-4 w-4" />
            </Link>
          </Button>
          <h1 className="text-2xl font-bold">Venta {sale.id.slice(0, 8)}</h1>
          <Badge className={statusColors[sale.status]}>{statusLabels[sale.status]}</Badge>
        </div>
      </div>

      {sale.status === "VOIDED" && sale.voidReason && (
        <div className="flex items-center gap-3 rounded-lg border border-yellow-200 bg-yellow-50 p-4 text-yellow-800">
          <AlertTriangle className="h-5 w-5" />
          <div>
            <p className="font-medium">Venta anulada</p>
            <p className="text-sm">Motivo: {sale.voidReason}</p>
            {sale.voidedAt && (
              <p className="text-sm">{new Date(sale.voidedAt).toLocaleString()}</p>
            )}
          </div>
        </div>
      )}

      {/* Items */}
      <Card>
        <CardHeader>
          <CardTitle>Items</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Producto</TableHead>
                <TableHead>Cantidad</TableHead>
                <TableHead>P. Unitario</TableHead>
                <TableHead>Total</TableHead>
                <TableHead>Lote</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sale.items.map((item) => (
                <TableRow key={item.itemId}>
                  <TableCell>
                    <div>
                      <p className="font-medium">{item.productName}</p>
                      {item.productSku && (
                        <p className="text-xs text-muted-foreground">{item.productSku}</p>
                      )}
                    </div>
                  </TableCell>
                  <TableCell>{item.quantity}</TableCell>
                  <TableCell>${item.unitPrice.toFixed(2)}</TableCell>
                  <TableCell>${item.totalPrice.toFixed(2)}</TableCell>
                  <TableCell>
                    <div className="space-y-1">
                      {item.batchAllocations.map((alloc, i) => (
                        <div key={i} className="text-xs text-muted-foreground">
                          {alloc.lotNumber} ({alloc.allocatedQuantity}u)
                        </div>
                      ))}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* Totals */}
      <div className="flex justify-end">
        <div className="w-64 space-y-2 rounded-lg border p-4">
          <div className="flex justify-between text-sm">
            <span>Subtotal</span>
            <span>${sale.subtotal.toFixed(2)}</span>
          </div>
          <div className="flex justify-between text-lg font-bold">
            <span>Total</span>
                    <span>${sale.total.toFixed(2)}</span>
          </div>
        </div>
      </div>

      {/* Void action */}
      {sale.status === "COMPLETED" && onVoid && (
        <div className="flex justify-end">
          <Button
            variant="destructive"
            onClick={() => {
              const reason = prompt("Motivo de anulación:");
              if (reason) onVoid(reason);
            }}
            disabled={isVoiding}
          >
            {isVoiding ? "Anulando..." : "Anular venta"}
          </Button>
        </div>
      )}
    </div>
  );
}
