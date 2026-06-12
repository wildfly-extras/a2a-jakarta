package org.wildfly.a2a.jakarta.common;

import java.net.URI;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.ext.Provider;

@Provider
@PreMatching
@Priority(50)
public class AgentCardRoutingFilter implements ContainerRequestFilter {

    private static final String AGENT_CARD_PATH = ".well-known/agent-card.json";

    @Inject
    Instance<A2AVersionProvider> allVersionProviders;

    private volatile boolean initialized;
    private A2AVersionProvider selectedProvider;

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    A2AVersionProvider best = null;
                    for (A2AVersionProvider provider : allVersionProviders) {
                        if (best == null || compareVersions(provider.getVersion(), best.getVersion()) > 0) {
                            best = provider;
                        }
                    }
                    selectedProvider = best;
                    initialized = true;
                }
            }
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!requestContext.getMethod().equals("GET")) {
            return;
        }

        String path = requestContext.getUriInfo().getPath().trim();
        if (!path.endsWith(AGENT_CARD_PATH)) {
            return;
        }

        ensureInitialized();

        if (selectedProvider == null) {
            return;
        }

        String prefix = selectedProvider.getInternalPathPrefix();
        String restBasePath = selectedProvider.getRestBasePath();
        if (restBasePath != null && !restBasePath.equals("/")) {
            prefix = prefix + restBasePath;
        }
        String newPath = prefix + (path.startsWith("/") ? path : "/" + path);

        URI baseUri = requestContext.getUriInfo().getBaseUri();
        URI requestUri = requestContext.getUriInfo().getRequestUri();
        URI newRequestUri = UriBuilder.fromUri(requestUri)
                .replacePath(baseUri.getPath() + newPath).build();
        requestContext.setRequestUri(baseUri, newRequestUri);
    }

    private static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        for (int i = 0; i < Math.max(parts1.length, parts2.length); i++) {
            int p1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int p2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (p1 != p2) return Integer.compare(p1, p2);
        }
        return 0;
    }
}
