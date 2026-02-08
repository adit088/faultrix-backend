# Spring Boot Ntropi Chaos APIs

[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-Build-blue)](https://maven.apache.org/)

**Ntropi Chaos APIs** is a high-performance, resilient backend orchestration layer designed for **Chaos Engineering** and system reliability testing. Unlike static testing tools, Ntropi provides a dynamic **Chaos Engine** that allows engineers to inject, manage, and analyze systemic failures in real-time.

By simulating unpredictable traffic patterns and architectural stressors, Ntropi helps teams move from "hoping it works" to "knowing it's resilient."

---

## 🚀 Key Features

* **Chaos Engine (Rules & Policies):** Define complex failure scenarios using a robust decision-making engine. Manage policies that dictate when and how faults are injected.
* **Runtime Configuration APIs:** Hot-swap chaos configurations without restarting services. Adjust blast radius and fault intensity on the fly.
* **Insight Engine:** Deep-dive analytics into system behavior during failure states. Correlate faults with performance degradation.
* **Traffic Simulation:** Generate realistic synthetic loads to test how the system scales—or breaks—under pressure.
* **User Management:** Secure RBAC (Role-Based Access Control) for managing who can trigger chaos experiments.
* **Automated Metrics Collection:** Native integration for monitoring system vitals during active experiments.
* **Global Exception Handling:** A unified error-tracking layer ensuring that even when things break, they break gracefully.

---

## 🛠 Tech Stack

* **Language:** Java 17+
* **Framework:** Spring Boot 3.x (Web, Data JPA, Security)
* **Build Tool:** Apache Maven
* **Database:** H2 (Dev) / PostgreSQL (Prod)
* **Observability:** Micrometer / Prometheus
* **Documentation:** SpringDoc OpenAPI (Swagger)

---

## 📂 Project Structure

```text
ntropi-chaos-apis/
├── src/main/java/com/ntropi/
│   ├── chaos/                 # Core Chaos Engine & Policy Logic
│   ├── config/                # Bean definitions & Security configs
│   ├── controller/            # REST Endpoints (User, Chaos, Metrics)
│   ├── exception/             # Global Exception Handler & Custom Errors
│   ├── insight/               # Behavior Analysis & Failure Insights
│   ├── model/                 # JPA Entities & DTOs
│   ├── repository/            # Persistence Layer
│   ├── service/               # Business Logic & Traffic Simulation
│   └── util/                  # Constants & Helpers
├── src/main/resources/
│   ├── application.yml        # Configuration
│   └── chaos-policies/        # Default JSON/YAML Chaos Rules
└── pom.xml                    # Project dependencies


Would you like me to generate a sample `ChaosPolicy` JSON or a Java snippet for the `Glob
