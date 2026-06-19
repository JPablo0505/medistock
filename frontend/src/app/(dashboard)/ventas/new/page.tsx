"use client";

import { useRouter } from "next/navigation";
import { SaleForm } from "@/components/sales/sale-form";
import { useCreateSale } from "@/hooks/use-sales";
import { useProducts } from "@/hooks/use-products";
import { Skeleton } from "@/components/ui/skeleton";
import { toast } from "sonner";

export default function NewSalePage() {
  const router = useRouter();
  const { data: products, isLoading } = useProducts({});
  const createSale = useCreateSale();

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-96 w-full" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <h1 className="text-3xl font-bold">Nueva venta</h1>

      <SaleForm
        products={products ?? []}
        isSubmitting={createSale.isPending}
        onSubmit={async (data) => {
          try {
            const result = await createSale.mutateAsync(data);
            toast.success("Venta creada exitosamente");
            router.push(`/ventas/${result.id}`);
          } catch (err) {
            const msg = err instanceof Error ? err.message : "Error al crear venta";
            toast.error(msg);
          }
        }}
        onCancel={() => router.push("/ventas")}
      />
    </div>
  );
}
