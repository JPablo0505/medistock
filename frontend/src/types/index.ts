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
  imageUrl?: string;
  salePrice: number;
  purchasePrice: number;
  isActive: boolean;
  expirationDate: string;
  manufacturingDate: string;
  stockQuantity: number;
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
  productName: string | null;
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

export type AlertSeverity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type AlertRuleType =
  | "EXPIRATION"
  | "LOW_STOCK"
  | "CONTROLLED_STOCK"
  | "PRESCRIPTION";

export interface AlertRule {
  id: string;
  name: string;
  description?: string;
  type: AlertRuleType;
  config: Record<string, unknown>;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export type AlertInstanceStatus = "ACTIVE" | "ACKNOWLEDGED" | "RESOLVED";

export interface AlertInstance {
  id: string;
  alertRuleId: string;
  productId: string;
  productName?: string;
  productDosageForm?: string;
  productManufacturer?: string;
  productStock?: number;
  productExpirationDate?: string;
  batchId?: string;
  severity: AlertSeverity;
  message: string;
  status: AlertInstanceStatus;
  createdAt: string;
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
