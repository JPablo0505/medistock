"use client";

import { useState, useMemo } from "react";
import Link from "next/link";
import { useProducts } from "@/hooks/use-products";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Plus, Search, RefreshCw, Package } from "lucide-react";

type FilterMode = "all" | "active" | "inactive";

function formatDate(dateStr: string): string {
  if (!dateStr) return "—";
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return dateStr;
  return d.toLocaleDateString("es-AR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
}

export default function ProductsPage() {
  const [search, setSearch] = useState("");
  const [filterMode, setFilterMode] = useState<FilterMode>("active");
  const [dosageForm, setDosageForm] = useState("");
  const [manufacturer, setManufacturer] = useState("");
  const {
    data: products,
    isLoading,
    error,
    refetch,
  } = useProducts(filterMode === "active" ? { active: true } : filterMode === "inactive" ? { active: false } : undefined);

  const uniqueDosageForms = useMemo(() => {
    if (!products) return [];
    const forms = new Set(products.map((p) => p.dosageForm).filter(Boolean));
    return Array.from(forms).sort();
  }, [products]);

  const filtered = useMemo(() => {
    if (!products) return [];
    let result = products;

    // Filter by name/generic name
    if (search.trim()) {
      const q = search.toLowerCase();
      result = result.filter(
        (p) =>
          p.name.toLowerCase().includes(q) ||
          p.genericName?.toLowerCase().includes(q)
      );
    }

    // Filter by dosage form
    if (dosageForm) {
      result = result.filter((p) => p.dosageForm === dosageForm);
    }

    // Filter by manufacturer
    if (manufacturer.trim()) {
      const q = manufacturer.toLowerCase();
      result = result.filter(
        (p) => p.manufacturer?.toLowerCase().includes(q)
      );
    }

    return result;
  }, [products, search, dosageForm, manufacturer]);

  // ─── Loading State ────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold tracking-tight">Products</h1>
        </div>

        <FilterBar
          search={search}
          onSearchChange={setSearch}
          filterMode={filterMode}
          onFilterModeChange={setFilterMode}
          dosageForm={dosageForm}
          onDosageFormChange={setDosageForm}
          manufacturer={manufacturer}
          onManufacturerChange={setManufacturer}
          uniqueDosageForms={uniqueDosageForms}
        />

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Card key={i}>
              <CardHeader>
                <Skeleton className="h-5 w-3/4" />
                <Skeleton className="h-4 w-1/2" />
              </CardHeader>
              <CardContent className="space-y-2">
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-2/3" />
                <Skeleton className="h-4 w-1/3" />
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    );
  }

  // ─── Error State ──────────────────────────────────────────────────────
  if (error) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <p className="text-lg font-medium text-destructive">
          Failed to load products
        </p>
        <p className="mt-1 text-sm text-muted-foreground">
          {(error as Error).message}
        </p>
        <Button variant="outline" className="mt-4" onClick={() => refetch()}>
          <RefreshCw className="mr-2 h-4 w-4" />
          Retry
        </Button>
      </div>
    );
  }

  // ─── Empty State ──────────────────────────────────────────────────────
  if (!products || products.length === 0) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold tracking-tight">Products</h1>
        </div>
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed py-20">
          <Package className="h-12 w-12 text-muted-foreground/50" />
          <p className="mt-4 text-lg font-medium">
            No products yet
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            Create your first product to get started.
          </p>
          <Link href="/products/new">
            <Button className="mt-6">
              <Plus className="mr-2 h-4 w-4" />
              Create Product
            </Button>
          </Link>
        </div>
      </div>
    );
  }

  // ─── Content State ────────────────────────────────────────────────────
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Products</h1>
        <Link href="/products/new">
          <Button>
            <Plus className="mr-2 h-4 w-4" />
            New Product
          </Button>
        </Link>
      </div>

      <FilterBar
        search={search}
        onSearchChange={setSearch}
        filterMode={filterMode}
        onFilterModeChange={setFilterMode}
        dosageForm={dosageForm}
        onDosageFormChange={setDosageForm}
        manufacturer={manufacturer}
        onManufacturerChange={setManufacturer}
        uniqueDosageForms={uniqueDosageForms}
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {filtered.map((product) => (
          <Link key={product.id} href={`/products/${product.id}`}>
            <Card className="h-full cursor-pointer transition-shadow hover:shadow-md">
              {/* 1. Imagen */}
              <div className="flex h-40 items-center justify-center overflow-hidden rounded-t-lg border-b bg-muted">
                {product.imageUrl ? (
                  /* eslint-disable-next-line @next/next/no-img-element */
                  <img
                    src={product.imageUrl}
                    alt={product.name}
                    className="h-full w-full object-contain p-2"
                    onError={(e) => {
                      (e.target as HTMLImageElement).style.display = "none";
                    }}
                  />
                ) : (
                  <Package className="h-12 w-12 text-muted-foreground/40" />
                )}
              </div>
              <CardHeader className="pb-2">
                {/* 7. Nombre del medicamento */}
                <div className="flex items-start justify-between gap-2">
                  <CardTitle className="line-clamp-2 text-base">
                    {product.name}
                  </CardTitle>
                  <Badge
                    variant={product.isActive ? "default" : "secondary"}
                    className="shrink-0"
                  >
                    {product.isActive ? "Active" : "Inactive"}
                  </Badge>
                </div>
              </CardHeader>
              <CardContent className="space-y-1 text-sm">
                {/* 2. Tipo (dosageForm) */}
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Tipo</span>
                  <span className="font-medium">{product.dosageForm || "—"}</span>
                </div>
                {/* 3. Fabricante */}
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Fabricante</span>
                  <span className="font-medium">{product.manufacturer || "—"}</span>
                </div>
                {/* 4. Unidad */}
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Unidad</span>
                  <span className="font-medium">{product.unit}</span>
                </div>
                {/* 5. Stock */}
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Stock</span>
                  <span className="font-medium">{product.stockQuantity}</span>
                </div>
                {/* Precio */}
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Precio</span>
                  <span className="font-medium">
                    {product.salePrice != null ? `$${Number(product.salePrice).toFixed(2)}` : "—"}
                  </span>
                </div>
                {/* 6. Fecha de vencimiento */}
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Fecha de vencimiento</span>
                  <span className="font-medium">
                    {formatDate(product.expirationDate)}
                  </span>
                </div>
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>

      {filtered.length === 0 && (
        <div className="flex flex-col items-center py-12 text-muted-foreground">
          <Search className="h-8 w-8" />
          <p className="mt-2">No products match your search.</p>
        </div>
      )}
    </div>
  );
}

// ─── Filter Bar Sub-component ──────────────────────────────────────────

function FilterBar({
  search,
  onSearchChange,
  filterMode,
  onFilterModeChange,
  dosageForm,
  onDosageFormChange,
  manufacturer,
  onManufacturerChange,
  uniqueDosageForms,
}: {
  search: string;
  onSearchChange: (v: string) => void;
  filterMode: FilterMode;
  onFilterModeChange: (v: FilterMode) => void;
  dosageForm: string;
  onDosageFormChange: (v: string) => void;
  manufacturer: string;
  onManufacturerChange: (v: string) => void;
  uniqueDosageForms: string[];
}) {
  return (
    <div className="space-y-3">
      {/* Row 1: Search input and filter toggle buttons */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[200px] max-w-md">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Buscar por nombre..."
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            className="pl-9"
          />
        </div>
        <div className="flex items-center gap-1 rounded-lg border p-1 bg-muted/30">
          <button
            type="button"
            onClick={() => onFilterModeChange("active")}
            className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
              filterMode === "active"
                ? "bg-primary text-primary-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            Activos
          </button>
          <button
            type="button"
            onClick={() => onFilterModeChange("inactive")}
            className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
              filterMode === "inactive"
                ? "bg-primary text-primary-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            Inactivos
          </button>
          <button
            type="button"
            onClick={() => onFilterModeChange("all")}
            className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
              filterMode === "all"
                ? "bg-primary text-primary-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            Todos
          </button>
        </div>
      </div>
      {/* Row 2: Additional filters */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="w-44">
          <Select
            value={dosageForm}
            onValueChange={(val) => onDosageFormChange(val === "__all" ? "" : val)}
          >
            <SelectTrigger>
              <SelectValue placeholder="Tipo (todas)" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__all">Todas las formas</SelectItem>
              {uniqueDosageForms.map((form) => (
                <SelectItem key={form} value={form}>
                  {form}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="relative flex-1 min-w-[160px] max-w-xs">
          <Input
            placeholder="Filtrar por fabricante..."
            value={manufacturer}
            onChange={(e) => onManufacturerChange(e.target.value)}
          />
        </div>
      </div>
    </div>
  );
}
