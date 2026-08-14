package com.vulnerable.invoice.Filters;

import com.vulnerable.invoice.TenantContext.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class TenantHeaderFilter extends OncePerRequestFilter {

    public static final String TenantHeader = "X-Tenant-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String tenantId = request.getHeader(TenantHeader);
        if (tenantId != null && !tenantId.isBlank()) {
            TenantContext.setTenantId(tenantId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clearTenantId();
        }
    }
}
