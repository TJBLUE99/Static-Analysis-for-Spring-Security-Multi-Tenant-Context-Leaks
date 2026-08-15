# Multi-Tenant Spring Boot `@Async` Security & SAST Guardrails

[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Semgrep](https://img.shields.io/badge/SAST-Semgrep-blue.svg)](https://semgrep.dev/)
[![Security](https://img.shields.io/badge/Category-AppSec%20%2F%20SAST-red.svg)]()

This repository demonstrates root-cause analysis, static analysis rule engineering (Semgrep), and production remediation for **`ThreadLocal` context loss across Spring `@Async` thread boundaries**.

Without thread propagation, asynchronous tasks in multi-tenant Java applications suffer from **silent data drops (null reads)** or severe **cross-tenant data leakage (Broken Object Level Authorization / BOLA)**.

---

## ⚠️ The Vulnerability: Implicit State Loss Across JVM Threads

In standard Spring Boot architectures, HTTP request context (such as `TenantId`, `Authentication`, or logging `MDC`) is stored in `ThreadLocal` memory managed by the HTTP worker thread (e.g., Tomcat execution thread).

When a method annotated with `@Async` is called, Spring hands execution off to a separate background thread pool (`ThreadPoolTaskExecutor`). Standard JVM thread pools **do not inherit `ThreadLocal` state**, causing the worker thread to execute under one of two failure modes:

```text
================================================================================================
                                 HTTP INGRESS LAYER (Thread #1)
================================================================================================
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│  HTTP Thread: [http-nio-8080-exec-1]                                                         │
│  ThreadLocal Memory: CURRENT_TENANT = "Tenant-B"                                             │
│                                                                                              │
│  Calls @Async generateInvoicePdf("inv-100")                                               │
└──────────────────────────────────────────────┬───────────────────────────────────────────────┘
                                               │
                                               │ ──► [ Async Hand-off Boundary ]
                                               │     (Standard Java DOES NOT copy
                                               │      ThreadLocal memory across threads!)
                                               ▼
================================================================================================
                               ASYNC WORKER POOL LAYER (Thread #2)
================================================================================================

      ┌────────────────────────────────────────┐      ┌────────────────────────────────────────┐
      │ SCENARIO A: Fresh Worker Thread        │      │ SCENARIO B: Recycled Worker Thread     │
      ├────────────────────────────────────────┤      ├────────────────────────────────────────┤
      │ ThreadLocal Memory: CURRENT_TENANT = null│    │ ThreadLocal Memory: CURRENT_TENANT = "A"│
      │                                        │      │ (Leftover from previous job!)          │
      │ Result: SILENT DATA LOSS / NULL READ   │      │ Result: CROSS-TENANT DATA LEAK         │
      └────────────────────────────────────────┘      └────────────────────────────────────────┘
