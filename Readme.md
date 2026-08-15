# Multi-Tenant Spring Boot `@Async` Security & SAST Guardrails

[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Semgrep](https://img.shields.io/badge/SAST-Semgrep-blue.svg)](https://semgrep.dev/)
[![Security](https://img.shields.io/badge/Category-AppSec%20%2F%20SAST-red.svg)]()

This repository demonstrates root-cause analysis, static analysis rule engineering (Semgrep), and production remediation for **`ThreadLocal` context loss across Spring `@Async` thread boundaries**.

Without thread propagation, asynchronous tasks in multi-tenant Java applications suffer from **silent data drops (null reads)** or severe **cross-tenant data leakage (Broken Object Level Authorization / BOLA)**.

---

## ⚠️ The Vulnerability: Implicit State Loss Across JVM Threads

In standard Spring Boot architectures, HTTP request context (such as `TenantId`, `Authentication`, or logging `MDC`) is stored in `ThreadLocal` memory managed by the caller thread (e.g., Tomcat execution thread).

When a method annotated with `@Async` is called, Spring hands execution off to a separate background thread pool (`ThreadPoolTaskExecutor`). Standard JVM thread pools **do not inherit `ThreadLocal` state**, causing the worker thread to execute under two primary failure modes:

* **Scenario A (Fresh Worker Thread):** The thread's `ThreadLocal` map is empty (`null`). Calls to `TenantContext.getTenantId()` return `null`, causing database queries to silently fail or return zero records.
* **Scenario B (Recycled Worker Thread):** The worker thread was previously used by another tenant and was not cleaned up. The thread reads leftover state from the previous execution, resulting in cross-tenant data contamination.

---

## 🔍 SAST Static Analysis Rules (Semgrep)

Custom Semgrep AST rules inspect the codebase in CI/CD pipelines to prevent unpropagated context vulnerabilities from merging into production.

### Rule 1: Flagging Unsafe `ThreadLocal` Access Inside `@Async` Methods
*Location: `invoice/semgrep-rules/missing-task-decorator-on-async-executor.yaml`*

```yaml
rules:
  - id: missing-task-decorator-on-async-executor
    languages: [ java ]
    severity: ERROR
    message: >
      A ThreadPoolTaskExecutor bean is instantiated without configuring a TaskDecorator.
      Async thread pools must use a TaskDecorator (e.g., ContextPropagatingTaskDecorator)
      to propagate ThreadLocal state (TenantContext, SecurityContext, MDC) to worker threads.
    metadata:
      cwe: "CWE-362: Concurrent Execution using Shared Resource with Improper Synchronization"
      category: security
      confidence: HIGH

    patterns:
      - pattern-inside: |
          @Bean(...)
          public $TYPE $METHOD(...) {
            ...
          }
      - pattern: |
          $EXEC = new ThreadPoolTaskExecutor(...);
      - pattern-not-inside: |
          ...
          $EXEC.setTaskDecorator(...);
          ...

## 🛠️ Implementation: Context-Propagating TaskDecorator

### 1. The Decorator (`ContextPropagatingTaskDecorator.java`)
Captures context on the caller HTTP thread before task dispatch, applies it to the background worker thread, and guarantees cleanup inside a `finally` block to prevent thread pool contamination.

```java
```java
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
        // 1. Capture context from the caller (HTTP) thread
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
