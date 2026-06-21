"use client";

import { useRouter } from "next/navigation";
import Link from "next/link";
import { useCreateProduct } from "@/hooks/use-products";
import { ProductForm, type ProductFormData } from "@/components/ProductForm";
import { Button } from "@/components/ui/button";
import { ArrowLeft } from "lucide-react";

export default function NewProductPage() {
  const router = useRouter();
  const createProduct = useCreateProduct();

  const handleSave = async (data: ProductFormData) => {
    const result = await createProduct.mutateAsync({
      name: data.name,
      genericName: data.genericName || undefined,
      categoryId: data.categoryId || undefined,
      concentration: data.concentration || undefined,
      dosageForm: data.dosageForm || undefined,
      manufacturer: data.manufacturer || undefined,
      requiresPrescription: data.requiresPrescription,
      isControlled: data.isControlled,
      storageCondition: data.storageCondition || undefined,
      unit: data.unit,
      imageUrl: data.imageUrl || undefined,
      salePrice: data.salePrice || undefined,
      purchasePrice: data.purchasePrice || undefined,
      expirationDate: data.expirationDate,
      manufacturingDate: data.manufacturingDate,
    });

    router.push(`/products/${result.id}`);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Link href="/products">
          <Button variant="ghost" size="icon">
            <ArrowLeft className="h-4 w-4" />
          </Button>
        </Link>
        <h1 className="text-2xl font-bold tracking-tight">New Product</h1>
      </div>

      <ProductForm onSave={handleSave} isSaving={createProduct.isPending} />
    </div>
  );
}
