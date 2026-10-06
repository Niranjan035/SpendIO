# ADR-0001: Modular monolith, feature-first packages

**Status:** Accepted · 2026-10-06

**Context.** SpendIO is a single-product personal finance app built by a small team. The
domain has hard consistency requirements (ledger ↔ budgets ↔ goals) that would become
distributed transactions under a service split.

**Decision.** One Spring Boot deployable organised into feature-first packages
(`transactions/`, `budgets/`, …) each owning its entities, repositories, services and
DTOs. Cross-module calls go through service interfaces only; ArchUnit enforces that
controllers never touch repositories directly and that no module reaches into another
module's repository or domain types.

**Consequences.** Simple operations and one database; a later extraction stays possible
because package boundaries already mirror service boundaries. We deliberately reject
microservices, a message broker, Redis and Kubernetes for v1.
