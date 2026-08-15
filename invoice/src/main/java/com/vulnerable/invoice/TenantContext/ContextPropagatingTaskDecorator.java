package com.vulnerable.invoice.TenantContext;

import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

public class ContextPropagatingTaskDecorator implements TaskDecorator {
    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
      
        String callerTenantId = TenantContext.getTenantId();
        SecurityContext callerSecurityContext = SecurityContextHolder.getContext();
        Map<String, String> callerMdcContext = MDC.getCopyOfContextMap();

        return () -> {
            try {

                if (callerTenantId != null) {
                    TenantContext.setTenantId(callerTenantId);
                }
                if (callerSecurityContext != null && callerSecurityContext.getAuthentication() != null) {
                    SecurityContextHolder.setContext(callerSecurityContext);
                }
                if (callerMdcContext != null) {
                    MDC.setContextMap(callerMdcContext);
                }

                runnable.run();

            } finally {
                TenantContext.clearTenantId();
                SecurityContextHolder.clearContext();
                MDC.clear();
            }
        };
    }

}
