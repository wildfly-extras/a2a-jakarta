package org.wildfly.a2a.jakarta.common;

public interface A2AVersionProvider {

    String getVersion();

    /**
     * Returns {@code true} if this is the default version for its transport.
     * <p>
     * Each {@link A2AVersionResolver} is scoped to a single transport (JSON-RPC or REST),
     * so a JSON-RPC provider and a REST provider may both return {@code true} without
     * conflicting — they are registered in separate resolvers.
     */
    boolean isDefaultVersion();

    String getInternalPathPrefix();

    /**
     * The client-facing REST base path for this version (e.g. {@code "/"} or {@code "/v1"}).
     * Return {@code null} for JSON-RPC-only providers.
     */
    String getRestBasePath();

    /**
     * Path prefixes that identify requests belonging to this REST version.
     * <p>
     * Used by the routing filter when {@link #getRestBasePath()} is {@code "/"} and
     * no {@code A2A-Version} header is present: only requests whose path starts with
     * one of these prefixes are routed to this version's internal path. This prevents
     * non-A2A paths (e.g. test utility endpoints) from being rewritten.
     * <p>
     * Providers with a non-root base path do not need this — the base path itself is
     * sufficient to identify matching requests.
     *
     * @return path prefixes (without leading slash), or empty to skip prefix matching
     */
    default java.util.Set<String> getRestPathPrefixes() {
        return java.util.Set.of();
    }
}
