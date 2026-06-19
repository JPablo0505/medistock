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

export type BatchStatus = "ACTIVE" | "EXPIRED" | "QUARANTINED" | "DEPLETED" | "DISPOSED";

export interface Batch {
  id: string;
  tenantId: string;
  productId: string;
  batchCode: string;
  initialQuantity: number;
  currentQuantity: number;
  expirationDate: string; // ISO date
  manufacturingDate: string | null;
  costPerUnit: number;
  status: BatchStatus;
  category: string | null;
  notes: string | null;
}

// ─── Movement ───────────────────────────────────────────────────────────────

export type MovementType = "RECEIPT" | "ADJUSTMENT" | "WRITE_OFF" | "TRANSFER" | "ALLOCATED" | "QUARANTINE_IN" | "QUARANTINE_OUT";

export interface Movement {
  id: string;
  batchId: string;
  productId: string;
  type: MovementType;
  quantity: number;
  previousQuantity: number;
  newQuantity: number;
  reason: string | null;
  performedBy: string | null;
  createdAt: string; // ISO datetime
}

// ─── Batch Detail ───────────────────────────────────────────────────────────

export interface BatchDetail {
  batch: Batch;
  movements: Movement[];
}

// ─── Stock Projection ───────────────────────────────────────────────────────

export interface StockProjection {
  productId: string;
  totalQuantity: number;
  availableQuantity: number;
  quarantinedQuantity: number;
  expiredQuantity: number;
  disposedQuantity: number;
  totalBatches: number;
  nearestExpiryDate: string | null;
}

// ─── Sale ───────────────────────────────────────────────────────────────────

export type SaleStatus = "COMPLETED" | "VOIDED";

export interface BatchAllocation {
  batchId: string;
  lotNumber: string;
  expirationDate: string;
  allocatedQuantity: number;
  costPerUnit: number;
}

export interface SaleItemResponse {
  itemId: string;
  productId: string;
  productName: string;
  productSku: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
  batchAllocations: BatchAllocation[];
}

export interface SaleResponse {
  id: string;
  saleDate: string;
  saleType: string;
  items: SaleItemResponse[];
  subtotal: number;
  total: number;
  status: SaleStatus;
  performedBy: string;
  voidedAt: string | null;
  voidReason: string | null;
  createdAt: string;
}

export interface SaleSummary {
  id: string;
  saleDate: string;
  itemCount: number;
  total: number;
  status: SaleStatus;
  performedBy: string;
  createdAt: string;
}

export interface CreateSaleItem {
  productId: string;
  productName?: string;
  productSku?: string;
  quantity: number;
  unitPrice: number;
}

export interface CreateSaleRequest {
  items: CreateSaleItem[];
  notes?: string;
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
