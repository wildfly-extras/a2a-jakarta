package org.wildfly.a2a.jakarta.common;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
@Priority(100)
public class A2AJsonRpcAcceptFilter implements ContainerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(A2AJsonRpcAcceptFilter.class);
    private static final String JSONRPC_PREFIX = "/a2a_jsonrpc_";
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*(\\d+|\"[^\"]*\"|null)");

    @Inject
    Instance<A2AVersionProvider> allVersionProviders;

    @Inject
    Instance<A2AJsonRpcMethodProvider> methodProviders;

    private volatile boolean initialized;
    private Set<String> allStreamingMethods;
    private Set<String> allNonStreamingMethods;
    private A2AVersionResolver versionResolver;

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    allStreamingMethods = new HashSet<>();
                    allNonStreamingMethods = new HashSet<>();
                    for (A2AJsonRpcMethodProvider provider : methodProviders) {
                        allStreamingMethods.addAll(provider.getStreamingMethodNames());
                        allNonStreamingMethods.addAll(provider.getNonStreamingMethodNames());
                    }
                    List<A2AVersionProvider> jsonRpcProviders = new ArrayList<>();
                    for (A2AVersionProvider provider : allVersionProviders) {
                        if (provider.getInternalPathPrefix().startsWith(JSONRPC_PREFIX)) {
                            jsonRpcProviders.add(provider);
                        }
                    }
                    versionResolver = new A2AVersionResolver(jsonRpcProviders);
                    initialized = true;
                }
            }
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!isJsonRpcRequest(requestContext)) {
            return;
        }

        ensureInitialized();

        if (!versionResolver.hasProviders()) {
            return;
        }

        String requestBody;
        try (InputStream entityInputStream = requestContext.getEntityStream()) {
            byte[] requestBodyBytes = entityInputStream.readAllBytes();
            requestBody = new String(requestBodyBytes);

            if (isStreamingRequest(requestBody)) {
                LOGGER.debug("Handling request as streaming: {}", requestBody);
                putAcceptHeader(requestContext, MediaType.SERVER_SENT_EVENTS);
            } else if (isNonStreamingRequest(requestBody)) {
                LOGGER.debug("Handling request as non-streaming: {}", requestBody);
                putAcceptHeader(requestContext, MediaType.APPLICATION_JSON);
            }

            requestContext.setEntityStream(new ByteArrayInputStream(requestBodyBytes));
        } catch (IOException e) {
            throw new RuntimeException("Unable to read the request body");
        }

        String versionHeader = requestContext.getHeaderString(A2AHeaders.A2A_VERSION);
        A2AVersionProvider provider = versionResolver.resolve(versionHeader);
        if (provider == null) {
            String requestId = extractJsonRpcId(requestBody);
            requestContext.abortWith(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32009,\"message\":\"Protocol version '"
                                    + versionHeader + "' is not supported. Supported versions: "
                                    + versionResolver.supportedVersionsString() + "\"},\"id\":" + requestId + "}")
                            .type(MediaType.APPLICATION_JSON)
                            .build());
            return;
        }

        URI baseUri = requestContext.getUriInfo().getBaseUri();
        URI requestUri = requestContext.getUriInfo().getRequestUri();
        String path = requestContext.getUriInfo().getPath();
        String newPath = provider.getInternalPathPrefix() + (path.startsWith("/") ? path : "/" + path);
        URI newRequestUri = UriBuilder.fromUri(requestUri).replacePath(baseUri.getPath() + newPath).build();
        requestContext.setRequestUri(baseUri, newRequestUri);
    }

    private boolean isJsonRpcRequest(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath().trim();
        return (path.equals("/") || path.isEmpty())
                && requestContext.getMethod().equals("POST")
                && requestContext.hasEntity();
    }

    private boolean isStreamingRequest(String requestBody) {
        for (String method : allStreamingMethods) {
            if (requestBody.contains(method)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNonStreamingRequest(String requestBody) {
        for (String method : allNonStreamingMethods) {
            if (requestBody.contains(method)) {
                return true;
            }
        }
        return false;
    }

    private static void putAcceptHeader(ContainerRequestContext requestContext, String mediaType) {
        requestContext.getHeaders().putSingle("Accept", mediaType);
    }

    private static String extractJsonRpcId(String requestBody) {
        Matcher matcher = ID_PATTERN.matcher(requestBody);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "null";
    }
}
