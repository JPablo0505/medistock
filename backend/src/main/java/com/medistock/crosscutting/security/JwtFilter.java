package com.medistock.crosscutting.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Extracts the tenant ID from the JWT token's {@code tenant_id} claim and
 * sets it in {@link TenantContext} for the duration of the request.
 *
 * <p>The token is read from the {@code Authorization: Bearer <token>} header.
 * If no token is present, the filter proceeds without setting a tenant context
 * (anonymous access).</p>
 */
public class JwtFilter extends OncePerRequestFilter {

    static final String AUTHORIZATION_HEADER = "Authorization";
    static final String BEARER_PREFIX = "Bearer ";
    static final String TENANT_ID_CLAIM = "tenant_id";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String header = request.getHeader(AUTHORIZATION_HEADER);
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                String token = header.substring(BEARER_PREFIX.length());
                DecodedJWT decoded = JWT.decode(token);
                String tenantId = decoded.getClaim(TENANT_ID_CLAIM).asString();
                if (tenantId != null && !tenantId.isBlank()) {
                    TenantContext.setTenantId(tenantId);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
