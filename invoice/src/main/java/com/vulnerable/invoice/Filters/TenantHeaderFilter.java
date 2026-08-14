package com.vulnerable.invoice.Filters;

import com.vulnerable.invoice.TenantContext.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
public class TenantHeaderFilter extends OncePerRequestFilter {

    public static final String TenantHeader = "X-Tenant-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        String tenantId = request.getHeader(TenantHeader);
        log.info("Tenant id from filter is: {}", tenantId);
        if (tenantId != null && !tenantId.isBlank()) {
            TenantContext.setTenantId(tenantId);
            var auth = new UsernamePasswordAuthenticationToken(
                    "user@" + tenantId,
                    null,
                    java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        try {
            log.info("Entering filterChain");
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clearTenantId();
        }
    }
}
