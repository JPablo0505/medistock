package com.medistock.crosscutting.security;

/**
 * ThreadLocal holder for the current tenant ID extracted from the JWT token.
 *
 * <p>The tenant ID is set by {@link JwtFilter} at the start of each request
 * and cleared in a {@code finally} block to prevent memory leaks.</p>
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
