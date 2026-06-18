// ─── Product ────────────────────────────────────────────────────────────────

export interface Product {
  id: string;
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
  isActive: boolean;
}

// ─── Category ───────────────────────────────────────────────────────────────

export interface Category {
  id: string;
  name: string;
  description?: string;
}

// ─── Batch ──────────────────────────────────────────────────────────────────

export type BatchStatus = "available" | "expired" | "depleted" | "quarantined";

export interface Batch {
  id: number;
  productId: number;
  batchCode: string;
  initialQuantity: number;
  currentQuantity: number;
  expirationDate: string; // ISO date
  manufacturingDate: string; // ISO date
  costPerUnit: number;
  status: BatchStatus;
  notes?: string;
}

// ─── Movement ───────────────────────────────────────────────────────────────

export type MovementType = "in" | "out" | "adjustment" | "transfer";

export interface Movement {
  id: number;
  batchId: number;
  productId: number;
  type: MovementType;
  quantity: number;
  referenceId?: string;
  reason?: string;
  unitCost?: number;
  createdAt: string; // ISO datetime
}

// ─── Sale ───────────────────────────────────────────────────────────────────

export type SaleStatus = "completed" | "cancelled" | "refunded";

export interface Sale {
  id: number;
  saleNumber: string;
  customerName?: string;
  totalAmount: number;
  status: SaleStatus;
  createdAt: string; // ISO datetime
}

export interface SaleLineItem {
  id: number;
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  batchId: number;
}

// ─── Alert ──────────────────────────────────────────────────────────────────

export type AlertSeverity = "low" | "medium" | "high" | "critical";
export type AlertRuleType =
  | "expiration"
  | "low_stock"
  | "controlled_stock"
  | "prescription";

export interface AlertRule {
  id: number;
  name: string;
  type: AlertRuleType;
  config: Record<string, unknown>;
  isActive: boolean;
}

export type AlertInstanceStatus = "active" | "acknowledged" | "resolved";

export interface AlertInstance {
  id: number;
  alertRuleId: number;
  productId: number;
  batchId?: number;
  severity: AlertSeverity;
  message: string;
  status: AlertInstanceStatus;
  createdAt: string; // ISO datetime
  acknowledgedAt?: string;
  resolvedAt?: string;
}

// ─── Pagination & Errors ────────────────────────────────────────────────────

export interface PaginatedResponse<T> {
  data: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export interface ErrorResponse {
  error: string;
  message: string;
  statusCode: number;
  details?: Record<string, string[]>;
}
