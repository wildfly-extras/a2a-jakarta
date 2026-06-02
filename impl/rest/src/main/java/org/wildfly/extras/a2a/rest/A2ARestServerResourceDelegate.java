package org.wildfly.extras.a2a.rest;

import static org.a2aproject.sdk.server.ServerCallContext.TRANSPORT_KEY;
import static org.a2aproject.sdk.transport.rest.context.RestContextKeys.HEADERS_KEY;
import static org.a2aproject.sdk.transport.rest.context.RestContextKeys.TENANT_KEY;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import org.a2aproject.sdk.common.A2AHeaders;
import org.wildfly.extras.a2a.common.SSESubscriber;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.server.extensions.A2AExtensions;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class A2ARestServerResourceDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(A2ARestServerResourceDelegate.class);
    private static final String PAGE_SIZE_PARAM = "pageSize";
    private static final String PAGE_TOKEN_PARAM = "pageToken";
    private static final String HISTORY_LENGTH_PARAM = "historyLength";
    private static final String STATUS_TIMESTAMP_AFTER = "statusTimestampAfter";

    private final RestHandler restHandler;

    private static volatile Runnable streamingIsSubscribedRunnable;

    public A2ARestServerResourceDelegate(RestHandler restHandler) {
        this.restHandler = restHandler;
    }

    @SuppressWarnings("ReturnValueIgnored")
    public Response sendMessage(String body, HttpServletRequest httpRequest, SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = null;
        try {
            response = restHandler.sendMessage(context, extractTenant(httpRequest), body);
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return Response.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .entity(response.getBody())
                    .build();
        }
    }

    public void sendMessageStreaming(String body, HttpServletRequest httpRequest, HttpServletResponse httpResponse, SecurityContext securityContext) throws IOException {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestStreamingResponse streamingResponse = null;
        RestHandler.HTTPRestResponse error = null;
        try {
            RestHandler.HTTPRestResponse response = restHandler.sendStreamingMessage(context, extractTenant(httpRequest), body);
            if (response instanceof RestHandler.HTTPRestStreamingResponse hTTPRestStreamingResponse) {
                streamingResponse = hTTPRestStreamingResponse;
            } else {
                error = response;
            }
        } finally {
            if (error != null) {
                sendErrorResponse(httpResponse, error);
            } else {
                handleCustomSSEResponse(streamingResponse.getPublisher(), httpResponse, context);
            }
        }
    }

    public void resubscribeTask(String taskId, HttpServletRequest httpRequest, HttpServletResponse httpResponse, SecurityContext securityContext) throws IOException {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestStreamingResponse streamingResponse = null;
        RestHandler.HTTPRestResponse error = null;
        try {
            RestHandler.HTTPRestResponse response = restHandler.subscribeToTask(context, extractTenant(httpRequest), taskId);
            if (response instanceof RestHandler.HTTPRestStreamingResponse hTTPRestStreamingResponse) {
                streamingResponse = hTTPRestStreamingResponse;
            } else {
                error = response;
            }
        } finally {
            if (error != null) {
                sendErrorResponse(httpResponse, error);
            } else {
                handleCustomSSEResponse(streamingResponse.getPublisher(), httpResponse, context);
            }
        }
    }

    public Response getAgentCard() {
        RestHandler.HTTPRestResponse response = restHandler.getAgentCard();

        String etag = "\"" + Integer.toHexString(response.getBody().hashCode()) + "\"";

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("GMT"));
        String lastModified = now.format(DateTimeFormatter.RFC_1123_DATE_TIME);

        return Response.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .header("Cache-Control", "max-age=3600")
                .header("ETag", etag)
                .header("Last-Modified", lastModified)
                .entity(response.getBody())
                .build();
    }

    public Response getAuthenticatedExtendedCard(HttpServletRequest httpRequest, SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = restHandler.getExtendedAgentCard(context, extractTenant(httpRequest));
        return Response.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .entity(response.getBody())
                .build();
    }

    public Response getExtendedAgentCard(HttpServletRequest httpRequest, SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = restHandler.getExtendedAgentCard(context, extractTenant(httpRequest));
        return Response.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .entity(response.getBody())
                .build();
    }

    @SuppressWarnings("ReturnValueIgnored")
    public Response listTasks(HttpServletRequest httpRequest, SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = null;
        try {
            String contextId = httpRequest.getParameter("contextId");
            String statusStr = httpRequest.getParameter("status");
            if (statusStr != null && !statusStr.isEmpty()) {
                statusStr = statusStr.toUpperCase();
            }
            String pageSizeStr = httpRequest.getParameter(PAGE_SIZE_PARAM);
            String pageToken = httpRequest.getParameter(PAGE_TOKEN_PARAM);
            String historyLengthStr = httpRequest.getParameter(HISTORY_LENGTH_PARAM);
            String statusTimestampAfter = httpRequest.getParameter(STATUS_TIMESTAMP_AFTER);
            String includeArtifactsStr = httpRequest.getParameter("includeArtifacts");

            Integer pageSize = null;
            if (pageSizeStr != null && !pageSizeStr.isEmpty()) {
                pageSize = Integer.valueOf(pageSizeStr);
            }

            Integer historyLength = null;
            if (historyLengthStr != null && !historyLengthStr.isEmpty()) {
                historyLength = Integer.valueOf(historyLengthStr);
            }

            Boolean includeArtifacts = null;
            if (includeArtifactsStr != null && !includeArtifactsStr.isEmpty()) {
                includeArtifacts = Boolean.valueOf(includeArtifactsStr);
            }

            response = restHandler.listTasks(context, extractTenant(httpRequest), contextId, statusStr, pageSize,
                    pageToken, historyLength, statusTimestampAfter, includeArtifacts);
        } catch (NumberFormatException e) {
            response = restHandler.createErrorResponse(new InvalidParamsError("Invalid number format in parameters"));
        } catch (IllegalArgumentException e) {
            response = restHandler.createErrorResponse(new InvalidParamsError("Invalid parameter value: " + e.getMessage()));
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return Response.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .entity(response.getBody())
                    .build();
        }
    }

    @SuppressWarnings("ReturnValueIgnored")
    public Response getTask(String taskId, String historyLengthStr, HttpServletRequest httpRequest, SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = null;
        try {
            Integer historyLength = null;
            if (historyLengthStr != null && !historyLengthStr.isEmpty()) {
                historyLength = Integer.valueOf(historyLengthStr);
            }
            response = restHandler.getTask(context, extractTenant(httpRequest), taskId, historyLength);
        } catch (NumberFormatException e) {
            response = restHandler.createErrorResponse(new InvalidParamsError("bad historyLength"));
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return Response.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .entity(response.getBody())
                    .build();
        }
    }

    @SuppressWarnings("ReturnValueIgnored")
    public Response cancelTask(String taskId, String body, HttpServletRequest httpRequest, SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = null;
        try {
            response = restHandler.cancelTask(context, extractTenant(httpRequest), body, taskId);
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return Response.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .entity(response.getBody())
                    .build();
        }
    }

    @SuppressWarnings("ReturnValueIgnored")
    public Response setTaskPushNotificationConfiguration(String taskId, String body, HttpServletRequest httpRequest, SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = null;
        try {
            response = restHandler.createTaskPushNotificationConfiguration(context, extractTenant(httpRequest), body, taskId);
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return Response.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .entity(response.getBody())
                    .build();
        }
    }

    @SuppressWarnings("ReturnValueIgnored")
    public Response getTaskPushNotificationConfiguration(String taskId, String configId, HttpServletRequest httpRequest, SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = null;
        try {
            response = restHandler.getTaskPushNotificationConfiguration(context, extractTenant(httpRequest), taskId, configId);
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return Response.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .entity(response.getBody())
                    .build();
        }
    }

    public Response getOrListTaskPushNotificationConfigurations(String taskId, HttpServletRequest httpRequest, SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = null;
        try {
            if (taskId == null || taskId.isEmpty()) {
                response = restHandler.createErrorResponse(new InvalidParamsError("bad task id"));
            } else {
                String requestURI = httpRequest.getRequestURI();
                if (requestURI.endsWith("/")) {
                    response = restHandler.getTaskPushNotificationConfiguration(context, extractTenant(httpRequest),
                            taskId, null);
                } else {
                    int pageSize = 0;
                    if (httpRequest.getParameter(PAGE_SIZE_PARAM) != null) {
                        pageSize = Integer.parseInt(httpRequest.getParameter(PAGE_SIZE_PARAM));
                    }
                    String pageToken = "";
                    if (httpRequest.getParameter(PAGE_TOKEN_PARAM) != null) {
                        pageToken = httpRequest.getParameter(PAGE_TOKEN_PARAM);
                    }
                    response = restHandler.listTaskPushNotificationConfigurations(context, extractTenant(httpRequest),
                            taskId, pageSize, pageToken);
                }
            }
        } catch (NumberFormatException e) {
            response = restHandler.createErrorResponse(new InvalidParamsError("bad " + PAGE_SIZE_PARAM));
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        }
        return Response.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .entity(response.getBody())
                .build();
    }

    @SuppressWarnings("ReturnValueIgnored")
    public Response deleteTaskPushNotificationConfiguration(String taskId, String configId, HttpServletRequest httpRequest, SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = null;
        try {
            response = restHandler.deleteTaskPushNotificationConfiguration(context, extractTenant(httpRequest), taskId, configId);
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return Response.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .entity(response.getBody())
                    .build();
        }
    }

    private void sendErrorResponse(HttpServletResponse httpResponse, RestHandler.HTTPRestResponse error) throws IOException {
        httpResponse.setStatus(error.getStatusCode());
        httpResponse.setHeader(CONTENT_TYPE, error.getContentType());
        httpResponse.getWriter().write(error.getBody());
        httpResponse.getWriter().flush();
    }

    private void handleCustomSSEResponse(Flow.Publisher<String> publisher,
            HttpServletResponse response,
            ServerCallContext context) throws IOException {
        response.setHeader(CONTENT_TYPE, MediaType.SERVER_SENT_EVENTS);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        CompletableFuture<Void> streamingComplete = new CompletableFuture<>();
        try (PrintWriter writer = response.getWriter()) {
            writer.write(": SSE stream started\n\n");
            writer.flush();
            publisher.subscribe(new SSESubscriber(streamingComplete, writer, context));
            streamingComplete.get();
        } catch (Exception e) {
            LOGGER.error("Error waiting for streaming completion: {}", e.getMessage(), e);
            throw new IOException("Streaming failed", e);
        }
    }

    public static void setStreamingIsSubscribedRunnable(Runnable streamingIsSubscribedRunnable) {
        A2ARestServerResourceDelegate.streamingIsSubscribedRunnable = streamingIsSubscribedRunnable;
        SSESubscriber.setStreamingIsSubscribedRunnable(streamingIsSubscribedRunnable);
    }

    protected ServerCallContext createCallContext(HttpServletRequest request, SecurityContext securityContext) {
        User user;

        if (securityContext.getUserPrincipal() == null) {
            user = UnauthenticatedUser.INSTANCE;
        } else {
            user = new User() {
                @Override
                public boolean isAuthenticated() {
                    return true;
                }

                @Override
                public String getUsername() {
                    return securityContext.getUserPrincipal().getName();
                }
            };
        }
        Map<String, Object> state = new HashMap<>();

        Map<String, String> headers = new HashMap<>();
        for (Enumeration<String> headerNames = request.getHeaderNames(); headerNames.hasMoreElements();) {
            String name = headerNames.nextElement();
            headers.put(name, request.getHeader(name));
        }

        state.put(HEADERS_KEY, headers);
        state.put(TENANT_KEY, extractTenant(request));
        state.put(TRANSPORT_KEY, TransportProtocol.HTTP_JSON);

        Enumeration<String> en = request.getHeaders(A2AHeaders.A2A_EXTENSIONS);
        List<String> extensionHeaderValues = new ArrayList<>();
        while (en.hasMoreElements()) {
            extensionHeaderValues.add(en.nextElement());
        }
        Set<String> requestedExtensions = A2AExtensions.getRequestedExtensions(extensionHeaderValues);
        String requestedVersion = request.getHeader(A2AHeaders.A2A_VERSION);
        return new ServerCallContext(user, state, requestedExtensions, requestedVersion);
    }

    private String extractTenant(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        if (requestURI == null || requestURI.isBlank()) {
            return "";
        }

        if (requestURI.startsWith("/")) {
            requestURI = requestURI.substring(1);
        }

        int slashIndex = requestURI.indexOf('/');
        int colonIndex = requestURI.indexOf(':');
        String firstSegment;

        if (colonIndex >= 0 && (slashIndex < 0 || colonIndex < slashIndex)) {
            firstSegment = requestURI.substring(0, colonIndex);
        } else if (slashIndex > 0) {
            firstSegment = requestURI.substring(0, slashIndex);
        } else {
            firstSegment = requestURI;
        }

        if (firstSegment.equals("message") ||
            firstSegment.equals("tasks") ||
            firstSegment.equals("card") ||
            firstSegment.equals("extendedAgentCard") ||
            firstSegment.equals(".well-known")) {
            return "";
        }

        return firstSegment;
    }
}
