"use client";

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
import { Plus } from "lucide-react";
import type { SaleSummary, SaleStatus } from "@/types";
import { Skeleton } from "@/components/ui/skeleton";

interface SaleListProps {
  sales: SaleSummary[] | undefined;
  isLoading: boolean;
  error: Error | null;
}

const statusLabels: Record<SaleStatus, string> = {
  COMPLETED: "Completada",
  VOIDED: "Anulada",
};

const statusColors: Record<SaleStatus, string> = {
  COMPLETED: "bg-green-100 text-green-800",
  VOIDED: "bg-gray-100 text-gray-800",
};

export function SaleList({ sales, isLoading, error }: SaleListProps) {
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
      <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-red-800">
        <p>Error al cargar ventas. Intente de nuevo.</p>
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
          <TableRow key={sale.id} className="cursor-pointer hover:bg-muted/50">
            <TableCell>
              <Link href={`/ventas/${sale.id}`} className="font-mono text-xs">
                {sale.id.slice(0, 8)}...
              </Link>
            </TableCell>
            <TableCell>{new Date(sale.saleDate).toLocaleDateString()}</TableCell>
            <TableCell>{sale.itemCount}</TableCell>
            <TableCell>${sale.total.toFixed(2)}</TableCell>
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
  );
}
