import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { Providers } from "@/components/providers";
import { Sidebar } from "@/components/sidebar";
import { Topbar } from "@/components/topbar";
import { Toaster } from "sonner";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "MediStock — Gestión de Inventario Farmacéutico",
  description:
    "Sistema de gestión de inventario para farmacias y distribuidoras farmacéuticas",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="es"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full">
        <Providers>
          <Sidebar />
          <div className="flex min-h-screen flex-col pl-64">
            <Topbar />
            <main className="flex-1 p-6">{children}</main>
          </div>
          <Toaster richColors />
        </Providers>
      </body>
    </html>
  );
}
