package org.wildfly.a2a.jakarta.itk;

import java.net.URI;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
@PreMatching
@Priority(50)
public class ItkPathRewriteFilter implements ContainerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItkPathRewriteFilter.class);

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.startsWith("/jsonrpc") || path.startsWith("/rest")) {
            String newPath = path.replaceFirst("^/(jsonrpc|rest)(/|$)", "/");
            LOGGER.debug("ITK path rewrite: {} -> {}", requestContext.getUriInfo().getPath(), newPath);
            URI baseUri = requestContext.getUriInfo().getBaseUri();
            URI requestUri = requestContext.getUriInfo().getRequestUri();
            URI newRequestUri = UriBuilder.fromUri(requestUri)
                    .replacePath(baseUri.getPath() + newPath)
                    .build();
            requestContext.setRequestUri(baseUri, newRequestUri);
        }
    }
}
