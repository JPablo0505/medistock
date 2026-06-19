"use client";

import { useParams } from "next/navigation";
import { SaleDetail } from "@/components/sales/sale-detail";
import { useSale, useVoidSale } from "@/hooks/use-sales";
import { toast } from "sonner";
import { useRouter } from "next/navigation";

export default function SaleDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { data: sale, isLoading, error } = useSale(id);
  const voidSale = useVoidSale();

  const handleVoid = async (reason: string) => {
    try {
      await voidSale.mutateAsync({ id, reason });
      toast.success("Venta anulada exitosamente");
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Error al anular venta";
      toast.error(msg);
    }
  };

  return (
    <SaleDetail
      sale={sale}
      isLoading={isLoading}
      error={error as Error | null}
      onVoid={handleVoid}
      isVoiding={voidSale.isPending}
    />
  );
}
