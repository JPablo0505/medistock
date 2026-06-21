"use client";

import { Search, Bell, User } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useActiveAlertCount } from "@/hooks/use-alerts";
import Link from "next/link";

export function Topbar() {
  const { data: alertCount, isLoading: alertCountLoading } = useActiveAlertCount();

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-4 border-b bg-background px-6">
      {/* Search */}
      <div className="relative flex-1 max-w-md">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          placeholder="Buscar productos, lotes..."
          className="pl-9"
        />
      </div>

      {/* Spacer */}
      <div className="flex-1" />

      {/* Actions */}
      <div className="flex items-center gap-2">
        <Link href="/alertas">
          <Button variant="ghost" size="icon" className="relative">
            <Bell className="h-4 w-4" />
            {alertCountLoading ? (
              <Skeleton className="absolute -right-0.5 -top-0.5 h-4 w-4 rounded-full" />
            ) : alertCount != null && alertCount > 0 ? (
              <span className="absolute -right-0.5 -top-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-destructive text-[10px] font-medium text-destructive-foreground">
                {alertCount > 9 ? "9+" : alertCount}
              </span>
            ) : null}
          </Button>
        </Link>
        <Button variant="ghost" size="icon">
          <User className="h-4 w-4" />
        </Button>
      </div>
    </header>
  );
}
