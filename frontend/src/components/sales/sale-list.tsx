"use client";

import { useState } from "react";
import Link from "next/link";
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
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Plus, Package, AlertTriangle, RefreshCw } from "lucide-react";
import { formatCOP } from "@/lib/format";
import type { SaleSummary, SaleStatus } from "@/types";
import { useSale } from "@/hooks/use-sales";

interface SaleListProps {
  sales: SaleSummary[] | undefined;
  isLoading: boolean;
  error: Error | null;
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

export function SaleList({ sales, isLoading, error, onRetry }: SaleListProps) {
  const [selectedSaleId, setSelectedSaleId] = useState<string | null>(null);
  const { data: saleDetail, isLoading: loadingDetail } = useSale(selectedSaleId ?? "");

  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 6 }).map((_, i) => (
          <Skeleton key={i} className="h-12 w-full" />
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center gap-3 rounded-lg border border-red-200 bg-red-50 p-4 text-red-800">
        <p>Error al cargar ventas. Intente de nuevo.</p>
        {onRetry && (
          <Button variant="outline" size="sm" onClick={onRetry}>
            <RefreshCw className="mr-2 h-4 w-4" />
            Reintentar
          </Button>
        )}
      </div>
    );
  }

  if (!sales || sales.length === 0) {
    return (
      <div className="flex flex-col items-center gap-4 py-16">
        <p className="text-lg text-muted-foreground">No hay ventas registradas</p>
        <Button asChild>
          <Link href="/ventas/new">
            <Plus className="mr-2 h-4 w-4" />
            Crear primera venta
          </Link>
        </Button>
      </div>
    );
  }

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>ID</TableHead>
            <TableHead>Fecha</TableHead>
            <TableHead>Items</TableHead>
            <TableHead>Total</TableHead>
            <TableHead>Estado</TableHead>
            <TableHead>Realizado por</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {sales.map((sale) => (
            <TableRow
              key={sale.id}
              className="cursor-pointer hover:bg-muted/50"
              onClick={() => setSelectedSaleId(sale.id)}
            >
              <TableCell>
                <span className="font-mono text-xs">
                  {sale.id.slice(0, 8)}...
                </span>
              </TableCell>
              <TableCell>{new Date(sale.saleDate).toLocaleDateString()}</TableCell>
              <TableCell>{sale.itemCount}</TableCell>
              <TableCell>{formatCOP(sale.total)}</TableCell>
              <TableCell>
                <Badge className={statusColors[sale.status]}>
                  {statusLabels[sale.status]}
                </Badge>
              </TableCell>
              <TableCell>{sale.performedBy}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      {/* Sale Detail Dialog */}
      <Dialog open={!!selectedSaleId} onOpenChange={(open) => { if (!open) setSelectedSaleId(null); }}>
        <DialogContent className="max-w-3xl max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {saleDetail ? (
                <span>
                  Venta {saleDetail.id.slice(0, 8)}...
                  <Badge className={`ml-2 ${statusColors[saleDetail.status]}`}>
                    {statusLabels[saleDetail.status]}
                  </Badge>
                </span>
              ) : "Detalle de venta"}
            </DialogTitle>
            <DialogDescription>
              {saleDetail && (
                <span>
                  {new Date(saleDetail.saleDate).toLocaleString("es-AR")} &middot;{" "}
                  Realizado por: {saleDetail.performedBy}
                </span>
              )}
            </DialogDescription>
          </DialogHeader>

          {loadingDetail && (
            <div className="space-y-3 py-4">
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-16 w-full" />
              <Skeleton className="h-16 w-full" />
            </div>
          )}

          {!loadingDetail && !saleDetail && (
            <div className="flex flex-col items-center gap-2 py-8 text-muted-foreground">
              <AlertTriangle className="h-8 w-8" />
              <p>No se pudo cargar el detalle de la venta.</p>
            </div>
          )}

          {!loadingDetail && saleDetail && (
            <div className="space-y-4">
              {/* Void notice */}
              {saleDetail.status === "VOIDED" && saleDetail.voidReason && (
                <div className="flex items-center gap-3 rounded-lg border border-yellow-200 bg-yellow-50 p-3 text-sm text-yellow-800">
                  <AlertTriangle className="h-4 w-4 shrink-0" />
                  <span>Anulada: {saleDetail.voidReason}</span>
                </div>
              )}

              {/* Items table */}
              <Card>
                <CardHeader className="py-3">
                  <CardTitle className="text-sm font-semibold">
                    Productos vendidos
                  </CardTitle>
                </CardHeader>
                <CardContent className="p-0">
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
                      {saleDetail.items.map((item) => (
                        <TableRow key={item.itemId}>
                          <TableCell>
                            <div className="flex items-center gap-2">
                              <Package className="h-4 w-4 text-muted-foreground" />
                              <div>
                                <p className="font-medium">{item.productName}</p>
                                {item.productSku && (
                                  <p className="text-xs text-muted-foreground">{item.productSku}</p>
                                )}
                              </div>
                            </div>
                          </TableCell>
                          <TableCell>{item.quantity}</TableCell>
                          <TableCell>{formatCOP(item.unitPrice)}</TableCell>
                          <TableCell>{formatCOP(item.totalPrice)}</TableCell>
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
                    <span>{formatCOP(saleDetail.subtotal)}</span>
                  </div>
                  <div className="flex justify-between text-lg font-bold">
                    <span>Total</span>
                    <span>{formatCOP(saleDetail.total)}</span>
                  </div>
                </div>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
