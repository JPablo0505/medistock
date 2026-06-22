"use client";

import { useState } from "react";
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
import { Plus, Trash2 } from "lucide-react";
import type { Product, CreateSaleItem } from "@/types";
import { formatCOP } from "@/lib/format";

interface SaleFormProps {
  products: Product[];
  onSubmit: (data: { items: CreateSaleItem[]; notes?: string }) => Promise<void>;
  onCancel: () => void;
  isSubmitting?: boolean;
}

interface LineItem {
  id: string;
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
}

export function SaleForm({ products, onSubmit, onCancel, isSubmitting }: SaleFormProps) {
  const [items, setItems] = useState<LineItem[]>([
    { id: crypto.randomUUID(), productId: "", productName: "", quantity: 1, unitPrice: 0 },
  ]);
  const [notes, setNotes] = useState("");

  const addLine = () => {
    setItems([...items, { id: crypto.randomUUID(), productId: "", productName: "", quantity: 1, unitPrice: 0 }]);
  };

  const removeLine = (id: string) => {
    if (items.length <= 1) return;
    setItems(items.filter((i) => i.id !== id));
  };

  const updateLine = (id: string, field: keyof LineItem, value: string | number) => {
    setItems(items.map((i) => {
      if (i.id !== id) return i;
      const updated = { ...i, [field]: value };

      // When product changes, auto-fill name and price
      if (field === "productId" && typeof value === "string") {
        const product = products.find((p) => p.id === value);
        if (product) {
          updated.productName = product.name;
          updated.unitPrice = product.salePrice;
        }
      }

      return updated;
    }));
  };

  const subtotal = items.reduce((sum, i) => sum + i.quantity * i.unitPrice, 0);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const saleItems: CreateSaleItem[] = items.map((i) => ({
      productId: i.productId,
      productName: i.productName,
      quantity: i.quantity,
      unitPrice: i.unitPrice,
    }));
    await onSubmit({ items: saleItems, notes: notes || undefined });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Items de la venta</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {items.map((item) => (
            <div key={item.id} className="flex items-end gap-3 rounded-lg border p-3">
              <div className="flex-1 space-y-1">
                <Label>Producto</Label>
                <Select
                  value={item.productId}
                  onValueChange={(v) => updateLine(item.id, "productId", v)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Seleccionar producto..." />
                  </SelectTrigger>
                  <SelectContent>
                    {products
                      .filter((p) => p.isActive)
                      .map((p) => (
                        <SelectItem key={p.id} value={p.id}>
                          {p.name}
                        </SelectItem>
                      ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="w-24 space-y-1">
                <Label>Cantidad</Label>
                <Input
                  type="number"
                  min={1}
                  value={item.quantity}
                  onChange={(e) =>
                    updateLine(item.id, "quantity", parseInt(e.target.value) || 0)
                  }
                />
              </div>

              <div className="w-28 space-y-1">
                <Label>P. Unitario</Label>
                <Input
                  type="number"
                  step="0.01"
                  min={0}
                  value={item.unitPrice}
                  onChange={(e) =>
                    updateLine(item.id, "unitPrice", parseFloat(e.target.value) || 0)
                  }
                />
              </div>

              <div className="w-24 pt-6 text-right text-sm text-muted-foreground">
                {formatCOP(item.quantity * item.unitPrice)}
              </div>

              <Button
                type="button"
                variant="ghost"
                size="icon"
                onClick={() => removeLine(item.id)}
                disabled={items.length <= 1}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          ))}

          <Button type="button" variant="outline" onClick={addLine} className="w-full">
            <Plus className="mr-2 h-4 w-4" />
            Agregar línea
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Notas</CardTitle>
        </CardHeader>
        <CardContent>
          <textarea
            className="w-full rounded-md border p-2 text-sm"
            rows={2}
            placeholder="Notas opcionales..."
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />
        </CardContent>
      </Card>

      <div className="flex items-center justify-between rounded-lg border p-4">
        <div>
          <p className="text-sm text-muted-foreground">Subtotal</p>
          <p className="text-2xl font-bold">{formatCOP(subtotal)}</p>
        </div>
        <div className="flex gap-3">
          <Button type="button" variant="outline" onClick={onCancel}>
            Cancelar
          </Button>
          <Button type="submit" disabled={isSubmitting || items.some((i) => !i.productId)}>
            {isSubmitting ? "Procesando..." : "Completar venta"}
          </Button>
        </div>
      </div>
    </form>
  );
}
