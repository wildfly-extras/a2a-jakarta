package org.wildfly.a2a.jakarta.common;

public interface A2AVersionProvider {

    String getVersion();

    boolean isDefaultVersion();

    String getInternalPathPrefix();

    /**
     * The client-facing REST base path for this version.
     * "/" for v1.0, "/v1" for v0.3.
     * Return null for JSON-RPC-only providers.
     */
    String getRestBasePath();
}
