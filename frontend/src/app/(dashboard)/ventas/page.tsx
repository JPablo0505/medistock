"use client";

import { Button } from "@/components/ui/button";
import { Plus } from "lucide-react";
import Link from "next/link";
import { SaleList } from "@/components/sales/sale-list";
import { useSales } from "@/hooks/use-sales";

export default function SalesPage() {
  const { data: sales, isLoading, error, refetch } = useSales();

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold">Ventas</h1>
        <Button asChild>
          <Link href="/ventas/new">
            <Plus className="mr-2 h-4 w-4" />
            Nueva venta
          </Link>
        </Button>
      </div>

      <SaleList sales={sales} isLoading={isLoading} error={error as Error | null} onRetry={() => refetch()} />
    </div>
  );
}
