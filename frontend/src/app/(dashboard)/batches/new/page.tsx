"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useCreateBatch } from "@/hooks/use-batches";
import { useProducts } from "@/hooks/use-products";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ArrowLeft, Loader2 } from "lucide-react";

export default function NewBatchPage() {
  const router = useRouter();
  const createBatch = useCreateBatch();
  const { data: products, isLoading: productsLoading } = useProducts({
    active: true,
  });

  const [productId, setProductId] = useState("");
  const [batchCode, setBatchCode] = useState("");
  const [quantity, setQuantity] = useState("");
  const [expirationDate, setExpirationDate] = useState("");
  const [manufacturingDate, setManufacturingDate] = useState("");
  const [costPerUnit, setCostPerUnit] = useState("");
  const [category, setCategory] = useState("");
  const [notes, setNotes] = useState("");

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!productId || !batchCode || !quantity || !expirationDate || !costPerUnit)
      return;

    try {
      const result = await createBatch.mutateAsync({
        productId,
        batchCode,
        quantity: Number(quantity),
        expirationDate,
        manufacturingDate: manufacturingDate || undefined,
        costPerUnit: Number(costPerUnit),
        category: category || undefined,
        notes: notes || undefined,
      });
      router.push(`/batches/${result.id}`);
    } catch {
      // error handled by mutation
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      {/* Back */}
      <Button asChild variant="ghost" size="sm">
        <Link href="/batches">
          <ArrowLeft className="mr-2 h-4 w-4" />
          Volver a lotes
        </Link>
      </Button>

      <div>
        <h1 className="text-2xl font-bold tracking-tight">Recibir lote</h1>
        <p className="text-sm text-muted-foreground">
          Registrá un nuevo lote en el inventario
        </p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-6 rounded-lg border p-6">
        {/* Product */}
        <div className="space-y-2">
          <Label htmlFor="product">Producto *</Label>
          {productsLoading ? (
            <Skeleton className="h-10 w-full" />
          ) : (
            <Select value={productId} onValueChange={setProductId}>
              <SelectTrigger>
                <SelectValue placeholder="Seleccionar producto..." />
              </SelectTrigger>
              <SelectContent>
                {(products ?? []).map((p) => (
                  <SelectItem key={p.id} value={p.id}>
                    {p.name} ({p.concentration ?? "sin concentración"})
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        </div>

        {/* Batch Code */}
        <div className="space-y-2">
          <Label htmlFor="batchCode">Código de lote *</Label>
          <Input
            id="batchCode"
            value={batchCode}
            onChange={(e) => setBatchCode(e.target.value)}
            placeholder="Ej: LOTE-001"
            required
          />
        </div>

        {/* Quantity */}
        <div className="space-y-2">
          <Label htmlFor="quantity">Cantidad *</Label>
          <Input
            id="quantity"
            type="number"
            min="1"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            placeholder="Ej: 100"
            required
          />
        </div>

        {/* Expiration Date */}
        <div className="space-y-2">
          <Label htmlFor="expiration">Fecha de vencimiento *</Label>
          <Input
            id="expiration"
            type="date"
            value={expirationDate}
            onChange={(e) => setExpirationDate(e.target.value)}
            required
          />
        </div>

        {/* Manufacturing Date */}
        <div className="space-y-2">
          <Label htmlFor="manufacturing">Fecha de fabricación</Label>
          <Input
            id="manufacturing"
            type="date"
            value={manufacturingDate}
            onChange={(e) => setManufacturingDate(e.target.value)}
          />
        </div>

        {/* Cost Per Unit */}
        <div className="space-y-2">
          <Label htmlFor="costPerUnit">Costo unitario *</Label>
          <Input
            id="costPerUnit"
            type="number"
            step="0.01"
            min="0"
            value={costPerUnit}
            onChange={(e) => setCostPerUnit(e.target.value)}
            placeholder="Ej: 15.50"
            required
          />
        </div>

        {/* Category */}
        <div className="space-y-2">
          <Label htmlFor="category">Categoría</Label>
          <Input
            id="category"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            placeholder="Ej: Analgésicos"
          />
        </div>

        {/* Notes */}
        <div className="space-y-2">
          <Label htmlFor="notes">Notas</Label>
          <Input
            id="notes"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Observaciones adicionales"
          />
        </div>

        {/* Submit */}
        <Button
          type="submit"
          className="w-full"
          disabled={
            createBatch.isPending ||
            !productId ||
            !batchCode ||
            !quantity ||
            !expirationDate ||
            !costPerUnit
          }
        >
          {createBatch.isPending && (
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          )}
          Recibir lote
        </Button>
      </form>
    </div>
  );
}
