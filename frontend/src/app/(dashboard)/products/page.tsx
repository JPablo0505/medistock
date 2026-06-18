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
import { Plus, Search, RefreshCw, Package } from "lucide-react";

export default function ProductsPage() {
  const [search, setSearch] = useState("");
  const [showAll, setShowAll] = useState(false);
  const {
    data: products,
    isLoading,
    error,
    refetch,
  } = useProducts(showAll ? undefined : { active: true });

  const filtered = useMemo(() => {
    if (!products) return [];
    if (!search.trim()) return products;
    const q = search.toLowerCase();
    return products.filter(
      (p) =>
        p.name.toLowerCase().includes(q) ||
        p.genericName?.toLowerCase().includes(q)
    );
  }, [products, search]);

  // ─── Loading State ────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold tracking-tight">Products</h1>
        </div>

        <SearchBar
          search={search}
          onSearchChange={setSearch}
          showAll={showAll}
          onToggleShowAll={() => setShowAll((v) => !v)}
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

      <SearchBar
        search={search}
        onSearchChange={setSearch}
        showAll={showAll}
        onToggleShowAll={() => setShowAll((v) => !v)}
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {filtered.map((product) => (
          <Link key={product.id} href={`/products/${product.id}`}>
            <Card className="h-full cursor-pointer transition-shadow hover:shadow-md">
              <CardHeader>
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
                {product.genericName && (
                  <p className="text-sm text-muted-foreground">
                    {product.genericName}
                  </p>
                )}
              </CardHeader>
              <CardContent className="space-y-1 text-sm">
                {product.concentration && (
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">
                      Concentration
                    </span>
                    <span className="font-medium">{product.concentration}</span>
                  </div>
                )}
                {product.dosageForm && (
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">Form</span>
                    <span className="font-medium">{product.dosageForm}</span>
                  </div>
                )}
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Unit</span>
                  <span className="font-medium">{product.unit}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Sale Price</span>
                  <span className="font-medium">
                    ${product.salePrice.toFixed(2)}
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

// ─── Search Bar Sub-component ──────────────────────────────────────────

function SearchBar({
  search,
  onSearchChange,
  showAll,
  onToggleShowAll,
}: {
  search: string;
  onSearchChange: (v: string) => void;
  showAll: boolean;
  onToggleShowAll: () => void;
}) {
  return (
    <div className="flex items-center gap-3">
      <div className="relative flex-1 max-w-md">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          placeholder="Search by name or generic name..."
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
          className="pl-9"
        />
      </div>
      <Button
        variant={showAll ? "default" : "outline"}
        size="sm"
        onClick={onToggleShowAll}
      >
        {showAll ? "All Products" : "Active Only"}
      </Button>
    </div>
  );
}
