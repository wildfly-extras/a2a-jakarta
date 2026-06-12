# Multiversion Routing Architecture

## Overview

Requests arrive at a single deployment URL and are routed to the correct transport+version endpoint via three `@PreMatching` JAX-RS filters in `http-common/`. Versions are discovered at runtime through CDI — each transport module provides `A2AVersionProvider` beans.

## Architecture Diagram

```
Incoming Request
    │
    ├─ GET /.well-known/agent-card.json
    │       ↓
    │   AgentCardRoutingFilter (@Priority 50)
    │       → picks highest version provider
    │       → rewrites to /{prefix}/.well-known/agent-card.json
    │
    ├─ POST / (Content-Type: application/json, JSON-RPC body)
    │       ↓
    │   A2AJsonRpcAcceptFilter (@Priority 100)
    │       → reads body, detects streaming vs non-streaming method
    │       → sets Accept header (SSE or JSON)
    │       → resolves version from A2A_VERSION header
    │       → rewrites to /a2a_jsonrpc_{version}/
    │
    └─ Other HTTP (REST endpoints like /message:send, /tasks/{id})
            ↓
        A2ARestVersionRoutingFilter (@Priority 200)
            → resolves version from A2A_VERSION header or base path
            → rewrites to /a2a_rest_{version}/{path}
```

## Core Components

### `A2AVersionProvider` (interface)

Each transport module registers CDI beans implementing this interface:

| Method | Purpose |
|--------|---------|
| `getVersion()` | Protocol version string (e.g., `"1.0"`, `"0.3"`) |
| `isDefaultVersion()` | Whether this is the default when no header is sent |
| `getInternalPathPrefix()` | Internal URI prefix (e.g., `/a2a_jsonrpc_v1.0`, `/a2a_rest_v0.3`) |
| `getRestBasePath()` | Client-facing REST base path (`"/"` for v1.0, `"/v1"` for v0.3, `null` for JSON-RPC-only) |

### `A2AVersionResolver`

Utility that maps version strings to providers. Each filter creates its own instance with a filtered subset:
- JSON-RPC filter: only providers whose prefix starts with `/a2a_jsonrpc_`
- REST filter: only providers with non-null `getRestBasePath()`

Resolution logic: if `A2A_VERSION` header is present, look up by version string. Otherwise, return the default provider (explicitly marked, or the sole provider in single-version deployments).

### `A2AJsonRpcMethodProvider` (interface)

Provides the set of streaming and non-streaming JSON-RPC method names. Used by `A2AJsonRpcAcceptFilter` to set the correct `Accept` header before the request reaches the JAX-RS resource.

## Filter Priority Order

| Priority | Filter | Handles |
|----------|--------|---------|
| 50 | `AgentCardRoutingFilter` | `GET /.well-known/agent-card.json` |
| 100 | `A2AJsonRpcAcceptFilter` | `POST /` with JSON-RPC body |
| 200 | `A2ARestVersionRoutingFilter` | REST requests (with version header or matching base path) |

Lower priority number = runs first. Agent card requests are handled before transport routing.

## Request Routing Examples

### Single-version deployment (v1.0 JSON-RPC + REST)

```
POST /  →  A2AJsonRpcAcceptFilter  →  /a2a_jsonrpc_v1.0/
GET /message:send  →  A2ARestVersionRoutingFilter  →  /a2a_rest_v1.0/message:send
```

### Multi-version deployment (v1.0 + v0.3)

```
POST / (A2A_VERSION: 1.0)  →  /a2a_jsonrpc_v1.0/
POST / (A2A_VERSION: 0.3)  →  /a2a_jsonrpc_v0.3/
GET /v1/message:send (no header)  →  /a2a_rest_v0.3/v1/message:send   (v0.3 base path match)
POST /message:send (A2A_VERSION: 1.0)  →  /a2a_rest_v1.0/message:send
```

### Key design choice: REST filter prepends, never strips

The REST filter prepends the internal prefix to the full incoming path — it does NOT strip the base path. This means v0.3 REST resources keep `/v1` in their `@Path` (e.g., `@Path("/a2a_rest_v0.3/v1")`). A request to `/v1/message:send` becomes `/a2a_rest_v0.3/v1/message:send`.

## Lazy Initialization

All three filters use double-checked locking to initialize on first request. This is necessary because CDI `Instance<T>` injection of version providers may not be fully resolved at filter construction time.

## Adding a New Protocol Version

1. Create a new module under `compat-{version}/` for the transport
2. Implement `A2AVersionProvider` as a CDI bean with the new version's prefix
3. For JSON-RPC, also implement `A2AJsonRpcMethodProvider`
4. Register JAX-RS resources under the internal path prefix
5. The filters will automatically discover the new version via CDI
