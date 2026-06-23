"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import {
  LayoutDashboard,
  Package,
  ClipboardList,
  ShoppingCart,
  AlertTriangle,
} from "lucide-react";

const navItems = [
  { label: "Inicio", href: "/", icon: LayoutDashboard },
  { label: "Productos", href: "/products", icon: Package },
  { label: "Inventario", href: "/batches", icon: ClipboardList },
  { label: "Ventas", href: "/ventas", icon: ShoppingCart },
  { label: "Alertas", href: "/alertas", icon: AlertTriangle },
];

export function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="fixed left-0 top-0 z-40 flex h-screen w-64 flex-col border-r bg-card">
      {/* Logo */}
      <div className="flex h-14 items-center border-b px-6">
        <Link href="/" className="flex items-center">
          <img src="/logo.png" alt="MediStock" className="h-8 w-auto" />
        </Link>
      </div>

      {/* Navigation */}
      <nav className="flex-1 space-y-1 p-4">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive =
            pathname === item.href ||
            (item.href !== "/" && pathname.startsWith(item.href + "/"));

          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                isActive
                  ? "bg-primary/10 text-primary"
                  : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
              )}
            >
              <Icon className="h-4 w-4" />
              {item.label}
            </Link>
          );
        })}
      </nav>

      {/* Footer */}
      <div className="border-t p-4">
        <p className="text-xs text-muted-foreground">
          &copy; {new Date().getFullYear()} MediStock
        </p>
      </div>
    </aside>
  );
}
