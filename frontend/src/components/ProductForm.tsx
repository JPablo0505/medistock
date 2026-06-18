"use client";

import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardFooter,
} from "@/components/ui/card";
import { useCategories } from "@/hooks/use-products";
import type { Product } from "@/types";

// ─── Types ──────────────────────────────────────────────────────────────

export interface ProductFormData {
  name: string;
  genericName: string;
  categoryId: string;
  concentration: string;
  dosageForm: string;
  manufacturer: string;
  requiresPrescription: boolean;
  isControlled: boolean;
  storageCondition: string;
  unit: string;
  salePrice: number;
  purchasePrice: number;
}

interface ProductFormProps {
  product?: Product;
  onSave: (data: ProductFormData) => Promise<void>;
  isSaving?: boolean;
}

const STORAGE_CONDITIONS = ["Room temperature", "Refrigerated", "Frozen"];

const defaultFormData: ProductFormData = {
  name: "",
  genericName: "",
  categoryId: "",
  concentration: "",
  dosageForm: "",
  manufacturer: "",
  requiresPrescription: false,
  isControlled: false,
  storageCondition: "Room temperature",
  unit: "",
  salePrice: 0,
  purchasePrice: 0,
};

// ─── Component ──────────────────────────────────────────────────────────

export function ProductForm({ product, onSave, isSaving }: ProductFormProps) {
  const { data: categories } = useCategories();
  const [form, setForm] = useState<ProductFormData>(defaultFormData);
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (product) {
      setForm({
        name: product.name,
        genericName: product.genericName,
        categoryId: product.categoryId ?? "",
        concentration: product.concentration,
        dosageForm: product.dosageForm,
        manufacturer: product.manufacturer,
        requiresPrescription: product.requiresPrescription,
        isControlled: product.isControlled,
        storageCondition: product.storageCondition,
        unit: product.unit,
        salePrice: product.salePrice,
        purchasePrice: product.purchasePrice,
      });
    }
  }, [product]);

  const validate = (): boolean => {
    const newErrors: Record<string, string> = {};
    if (!form.name.trim()) newErrors.name = "Name is required";
    if (!form.unit.trim()) newErrors.unit = "Unit is required";
    if (form.salePrice < 0)
      newErrors.salePrice = "Sale price must be 0 or greater";
    if (form.purchasePrice < 0)
      newErrors.purchasePrice = "Purchase price must be 0 or greater";
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    await onSave(form);
  };

  const updateField = <K extends keyof ProductFormData>(
    key: K,
    value: ProductFormData[K]
  ) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    if (errors[key]) {
      setErrors((prev) => {
        const next = { ...prev };
        delete next[key];
        return next;
      });
    }
  };

  const inputClass =
    "flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 md:text-sm";

  const selectClass =
    "flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 md:text-sm";

  const isEdit = !!product;

  return (
    <Card>
      <CardHeader>
        <CardTitle>{isEdit ? "Edit Product" : "New Product"}</CardTitle>
      </CardHeader>
      <form onSubmit={handleSubmit}>
        <CardContent className="space-y-6">
          {/* Basic Information */}
          <div className="space-y-4">
            <h3 className="text-sm font-medium text-muted-foreground">
              Basic Information
            </h3>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <label htmlFor="name" className="text-sm font-medium">
                  Name <span className="text-destructive">*</span>
                </label>
                <Input
                  id="name"
                  value={form.name}
                  onChange={(e) => updateField("name", e.target.value)}
                  placeholder="Product name"
                  className={errors.name ? "border-destructive" : ""}
                />
                {errors.name && (
                  <p className="text-xs text-destructive">{errors.name}</p>
                )}
              </div>

              <div className="space-y-2">
                <label htmlFor="genericName" className="text-sm font-medium">
                  Generic Name
                </label>
                <Input
                  id="genericName"
                  value={form.genericName}
                  onChange={(e) =>
                    updateField("genericName", e.target.value)
                  }
                  placeholder="Generic name"
                />
              </div>

              <div className="space-y-2">
                <label htmlFor="categoryId" className="text-sm font-medium">
                  Category
                </label>
                <select
                  id="categoryId"
                  value={form.categoryId}
                  onChange={(e) => updateField("categoryId", e.target.value)}
                  className={selectClass}
                >
                  <option value="">Select a category</option>
                  {categories?.map((cat) => (
                    <option key={cat.id} value={cat.id}>
                      {cat.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-2">
                <label htmlFor="manufacturer" className="text-sm font-medium">
                  Manufacturer
                </label>
                <Input
                  id="manufacturer"
                  value={form.manufacturer}
                  onChange={(e) =>
                    updateField("manufacturer", e.target.value)
                  }
                  placeholder="Manufacturer name"
                />
              </div>

              <div className="space-y-2">
                <label htmlFor="concentration" className="text-sm font-medium">
                  Concentration
                </label>
                <Input
                  id="concentration"
                  value={form.concentration}
                  onChange={(e) =>
                    updateField("concentration", e.target.value)
                  }
                  placeholder="e.g. 400mg"
                />
              </div>

              <div className="space-y-2">
                <label htmlFor="dosageForm" className="text-sm font-medium">
                  Dosage Form
                </label>
                <Input
                  id="dosageForm"
                  value={form.dosageForm}
                  onChange={(e) =>
                    updateField("dosageForm", e.target.value)
                  }
                  placeholder="e.g. Tablets"
                />
              </div>

              <div className="space-y-2">
                <label htmlFor="unit" className="text-sm font-medium">
                  Unit <span className="text-destructive">*</span>
                </label>
                <Input
                  id="unit"
                  value={form.unit}
                  onChange={(e) => updateField("unit", e.target.value)}
                  placeholder="e.g. tablets, ml, units"
                  className={errors.unit ? "border-destructive" : ""}
                />
                {errors.unit && (
                  <p className="text-xs text-destructive">{errors.unit}</p>
                )}
              </div>

              <div className="space-y-2">
                <label
                  htmlFor="storageCondition"
                  className="text-sm font-medium"
                >
                  Storage Condition
                </label>
                <select
                  id="storageCondition"
                  value={form.storageCondition}
                  onChange={(e) =>
                    updateField("storageCondition", e.target.value)
                  }
                  className={selectClass}
                >
                  {STORAGE_CONDITIONS.map((condition) => (
                    <option key={condition} value={condition}>
                      {condition}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>

          {/* Pricing */}
          <div className="space-y-4">
            <h3 className="text-sm font-medium text-muted-foreground">
              Pricing
            </h3>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <label htmlFor="salePrice" className="text-sm font-medium">
                  Sale Price
                </label>
                <Input
                  id="salePrice"
                  type="number"
                  min={0}
                  step={0.01}
                  value={form.salePrice}
                  onChange={(e) =>
                    updateField("salePrice", parseFloat(e.target.value) || 0)
                  }
                  placeholder="0.00"
                  className={errors.salePrice ? "border-destructive" : ""}
                />
                {errors.salePrice && (
                  <p className="text-xs text-destructive">
                    {errors.salePrice}
                  </p>
                )}
              </div>

              <div className="space-y-2">
                <label
                  htmlFor="purchasePrice"
                  className="text-sm font-medium"
                >
                  Purchase Price
                </label>
                <Input
                  id="purchasePrice"
                  type="number"
                  min={0}
                  step={0.01}
                  value={form.purchasePrice}
                  onChange={(e) =>
                    updateField(
                      "purchasePrice",
                      parseFloat(e.target.value) || 0
                    )
                  }
                  placeholder="0.00"
                  className={errors.purchasePrice ? "border-destructive" : ""}
                />
                {errors.purchasePrice && (
                  <p className="text-xs text-destructive">
                    {errors.purchasePrice}
                  </p>
                )}
              </div>
            </div>
          </div>

          {/* Flags */}
          <div className="space-y-4">
            <h3 className="text-sm font-medium text-muted-foreground">
              Flags & Requirements
            </h3>
            <div className="flex flex-wrap gap-6">
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={form.requiresPrescription}
                  onChange={(e) =>
                    updateField("requiresPrescription", e.target.checked)
                  }
                  className="h-4 w-4 rounded border-input accent-primary"
                />
                Requires Prescription
              </label>

              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={form.isControlled}
                  onChange={(e) =>
                    updateField("isControlled", e.target.checked)
                  }
                  className="h-4 w-4 rounded border-input accent-primary"
                />
                Controlled Substance
              </label>
            </div>
          </div>
        </CardContent>

        <CardFooter className="flex justify-end gap-3 border-t px-6 py-4">
          <Button type="submit" disabled={isSaving}>
            {isSaving
              ? "Saving..."
              : isEdit
                ? "Update Product"
                : "Create Product"}
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}
