package org.wildfly.a2a.jakarta.common;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.ext.Provider;

import org.a2aproject.sdk.common.A2AHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
@PreMatching
@Priority(200)
public class A2ARestVersionRoutingFilter implements ContainerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(A2ARestVersionRoutingFilter.class);
    private static final String A2A_INTERNAL_PREFIX = "/a2a_";

    @Inject
    Instance<A2AVersionProvider> allVersionProviders;

    private volatile boolean initialized;
    private A2AVersionResolver versionResolver;
    private Set<String> knownRestBasePaths;

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    List<A2AVersionProvider> restProviders = new ArrayList<>();
                    knownRestBasePaths = new TreeSet<>(Comparator.comparingInt(String::length).reversed());
                    for (A2AVersionProvider provider : allVersionProviders) {
                        String basePath = provider.getRestBasePath();
                        if (basePath != null) {
                            restProviders.add(provider);
                            knownRestBasePaths.add(basePath);
                        }
                    }
                    versionResolver = new A2AVersionResolver(restProviders);
                    initialized = true;
                }
            }
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath().trim();

        if (path.startsWith("/.well-known/")) {
            return;
        }
        if (path.startsWith(A2A_INTERNAL_PREFIX)) {
            return;
        }

        ensureInitialized();

        if (!versionResolver.hasProviders()) {
            return;
        }

        String versionHeader = requestContext.getHeaderString(A2AHeaders.A2A_VERSION);

        if (versionHeader == null) {
            boolean matchesNonRootBasePath = false;
            for (String basePath : knownRestBasePaths) {
                if (!basePath.equals("/") && (path.startsWith(basePath + "/") || path.equals(basePath))) {
                    matchesNonRootBasePath = true;
                    break;
                }
            }
            if (!matchesNonRootBasePath) {
                return;
            }
        }

        A2AVersionProvider provider = versionResolver.resolve(versionHeader);
        if (provider == null) {
            requestContext.abortWith(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("{\"error\":{\"code\":-32001,\"message\":\"Protocol version '"
                                    + versionHeader + "' is not supported. Supported versions: "
                                    + versionResolver.supportedVersionsString() + "\"}}")
                            .type(MediaType.APPLICATION_JSON)
                            .build());
            return;
        }

        String newPath = provider.getInternalPathPrefix() + (path.startsWith("/") ? path : "/" + path);

        LOGGER.debug("REST version routing: {} -> {}", path, newPath);

        URI baseUri = requestContext.getUriInfo().getBaseUri();
        URI requestUri = requestContext.getUriInfo().getRequestUri();
        URI newRequestUri = UriBuilder.fromUri(requestUri).replacePath(baseUri.getPath() + newPath).build();
        requestContext.setRequestUri(baseUri, newRequestUri);
    }
}
