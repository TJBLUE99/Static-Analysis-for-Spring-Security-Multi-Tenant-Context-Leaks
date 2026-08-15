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
*Location: `.semgrep/threadlocal-read-inside-async-method.yaml`*

```yaml
rules:
  - id: threadlocal-read-inside-async-method
    languages: [ java ]
    severity: ERROR
    message: > 
      A ThreadLocal-backed context accessor ($CTXCLASS.$ACCESSOR) was detected inside an @Async execution scope.
      Async worker threads execute on a separate thread pool and will not inherit caller ThreadLocal context,
      leading to null reads or cross-tenant data contamination.
    metadata:
      cwe: "CWE-362: Concurrent Execution using Shared Resource with Improper Synchronization"
      owasp: "A01:2021 - Broken Access Control"
      category: security
      confidence: HIGH

    patterns:
      - pattern-either:
          - pattern-inside: |
              @Async
              $RETTYPE$METHOD(...) { ... }
          - pattern-inside: |
              @Async(...)
              $RETTYPE$METHOD(...) { ... }
          - pattern-inside: |
              @Async
              class $CLASS { ... }
          - pattern-inside: |
              @Async(...)
              class $CLASS { ... }
      - pattern: $CTXCLASS.$ACCESSOR(...)
      - metavariable-regex:
          metavariable: $ACCESSOR
          regex: ^(get.*|get|current.*)$
      - metavariable-regex:
          metavariable: $CTXCLASS
          regex: .*Context.*
