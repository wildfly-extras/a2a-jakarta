# AGENTS.md

## Project Overview

Jakarta EE integration for the [A2A Java SDK](https://github.com/a2aproject/a2a-java), providing A2A protocol support on Jakarta servers (WildFly, Tomcat, Jetty, OpenLiberty). Multi-module Maven project (`org.wildfly.a2a` group) implementing JSON-RPC, gRPC, and REST transports via CDI-discovered version routing.

This project does **not** implement the A2A protocol itself — it adapts the [a2a-java SDK](https://github.com/a2aproject/a2a-java) (`org.a2aproject.sdk`, version controlled by `version.sdk` in the root POM) for deployment in Jakarta EE containers.

## Build

Requires Java 17+. Test output is redirected to files by default.

```bash
mvn clean install
```

## Project Structure

- `http-common/` — Shared CDI filters and version routing (the core routing layer)
  - `A2AJsonRpcAcceptFilter` — `@PreMatching` filter detecting JSON-RPC requests, setting Accept headers, routing to versioned endpoints
  - `A2ARestVersionRoutingFilter` — `@PreMatching` filter routing REST requests by `A2A_VERSION` header
  - `AgentCardRoutingFilter` — Routes `/.well-known/agent-card.json` to the highest version's endpoint
  - `A2AVersionProvider` / `A2AVersionResolver` — CDI-based version discovery and resolution
- `impl/` — Transport implementations (each provides `A2AVersionProvider` beans)
  - `jsonrpc/` — JSON-RPC transport (JAX-RS resource + SSE streaming)
  - `rest/` — REST transport (JAX-RS resource)
  - `grpc/` — gRPC transport (WildFly gRPC subsystem integration)
- `compat-0.3/` — Backward compatibility for A2A protocol v0.3
  - `jsonrpc/`, `rest/`, `grpc/` — v0.3 versions of each transport
  - `tck/` — TCK runner for v0.3 compatibility testing
- `tck/` — Technology Compatibility Kit runner (deploys to WildFly with Arquillian)
- `tests/` — Integration tests
  - `jsonrpc/`, `rest/`, `grpc/` — Per-transport tests
  - `multiversion/` — Multi-version routing tests (v1.0 + v0.3 deployed together)
  - `compat-0.3/` — v0.3-only compatibility tests
- `examples/` — Sample applications (e.g., `simple/`)

## Key Conventions

- Package root: `org.wildfly.a2a.jakarta`
- Depends on A2A Java SDK: `org.a2aproject.sdk` (version set by `version.sdk` property)
- Serialization: Jackson (provided by application servers like WildFly)
- Runtime: Jakarta EE (CDI, JAX-RS, Servlet)
- Testing: JUnit 5, Arquillian (deployed to WildFly), REST Assured
- Application packaging: `.war` files

### Code Style

- Import statements are sorted
- Remove any unused import statements
- Do not use "star" imports (e.g., `import java.util.*`)
- Use `@Provider` and `@PreMatching` for JAX-RS filters
- CDI injection via `Instance<T>` for discovering version providers
- Lazy initialization with double-checked locking for filter state

### Code Generation

- Be concise
- Try to use existing code instead of generating new similar code
- Use the same code conventions as existing code. If the existing convention seems incorrect, suggest changes before making them

### PR Instructions

- Follow [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/#summary) for the commit title and message
- Always ask if the commit is related to a GitHub issue. If so, add `This fixes #<issue_number>` at the end of the commit message

## Architecture Deep Dives

For detailed architectural documentation:

- **[Multiversion Routing](.claude/architecture/MULTIVERSION_ROUTING.md)**: Filter-based CDI version routing — how requests are routed to transport+version-specific endpoints via `@PreMatching` filters and CDI-discovered `A2AVersionProvider` beans

> Deep-dive docs are loaded on-demand when working in related areas.

## Relationship to a2a-java SDK

This project wraps the [a2a-java SDK](https://github.com/a2aproject/a2a-java):

- **SDK provides**: Protocol types (`spec/`), client SDK, server-common (`AgentExecutor`, `TaskStore`, `EventQueue`), transport SPIs
- **This project provides**: Jakarta EE integration — CDI producers, JAX-RS resources, `@PreMatching` filters, WildFly gRPC integration, `.war` packaging patterns

Users implement `AgentExecutor` and produce `AgentCard` via CDI, then package with the appropriate transport modules in a `.war`.

## Contributing

See the project [README.md](README.md) for packaging and usage details.
