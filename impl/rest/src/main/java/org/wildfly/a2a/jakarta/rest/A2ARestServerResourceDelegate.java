package org.wildfly.a2a.jakarta.rest;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static org.a2aproject.sdk.server.ServerCallContext.TRANSPORT_KEY;
import static org.a2aproject.sdk.spec.A2AMethods.CANCEL_TASK_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.DELETE_TASK_PUSH_NOTIFICATION_CONFIG_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.GET_EXTENDED_AGENT_CARD_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.GET_TASK_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.GET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.LIST_TASK_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.LIST_TASK_PUSH_NOTIFICATION_CONFIG_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.SEND_MESSAGE_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.SEND_STREAMING_MESSAGE_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.SET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.SUBSCRIBE_TO_TASK_METHOD;
import static org.a2aproject.sdk.transport.rest.context.RestContextKeys.HEADERS_KEY;
import static org.a2aproject.sdk.transport.rest.context.RestContextKeys.METHOD_NAME_KEY;
import static org.a2aproject.sdk.transport.rest.context.RestContextKeys.TENANT_KEY;

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
import java.util.concurrent.ScheduledExecutorService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import org.a2aproject.sdk.common.A2AHeaders;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.server.extensions.A2AExtensions;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.a2a.jakarta.common.A2ARequestAttributes;
import org.wildfly.a2a.jakarta.common.SSESubscriber;
import org.wildfly.a2a.jakarta.common.TenantHolder;

public class A2ARestServerResourceDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(A2ARestServerResourceDelegate.class);
    private static final String PAGE_SIZE_PARAM = "pageSize";
    private static final String PAGE_TOKEN_PARAM = "pageToken";
    private static final String HISTORY_LENGTH_PARAM = "historyLength";
    private static final String STATUS_TIMESTAMP_AFTER = "statusTimestampAfter";

    private final RestHandler restHandler;
    private final TenantHolder tenantHolder;
    private final ScheduledExecutorService heartbeatScheduler;

    public A2ARestServerResourceDelegate(RestHandler restHandler, TenantHolder tenantHolder, ScheduledExecutorService heartbeatScheduler) {
        this.restHandler = restHandler;
        this.tenantHolder = tenantHolder;
        this.heartbeatScheduler = heartbeatScheduler;
    }

    public Response sendMessage(String body, HttpServletRequest httpRequest, SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext, SEND_MESSAGE_METHOD);
        RestHandler.HTTPRestResponse response;
        try {
            response = restHandler.sendMessage(context, readTenant(httpRequest), body);
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            LOGGER.error("Internal error while processing request", t);
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError("Internal error"));
        }
        return Response.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .entity(response.getBody())
                .build();
    }

    public void sendMessageStreaming(String body, HttpServletRequest httpRequest, HttpServletResponse httpResponse, SecurityContext securityContext) throws IOException {
        LOGGER.info("REST sendMessageStreaming called from {}", httpRequest.getRemoteAddr());
        ServerCallContext context = createCallContext(httpRequest, securityContext, SEND_STREAMING_MESSAGE_METHOD);
        RestHandler.HTTPRestStreamingResponse streamingResponse = null;
        RestHandler.HTTPRestResponse error = null;
        try {
            // Streaming path: read the tenant from the @RequestScoped TenantHolder, not the servlet request attribute — the attribute is not reliably visible on the RESTEasy SSE async writer thread (see the Task 2 spike); the holder is re-associated on the async thread within the same request.
            RestHandler.HTTPRestResponse response = restHandler.sendStreamingMessage(context, tenantHolder.getTenant(), body);
            if (response instanceof RestHandler.HTTPRestStreamingResponse hTTPRestStreamingResponse) {
                streamingResponse = hTTPRestStreamingResponse;
            } else {
                error = response;
            }
        } catch (Throwable t) {
            LOGGER.error("Internal error while processing streaming request", t);
            error = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError("Internal error"));
        }
        if (error != null) {
            LOGGER.info("REST sendMessageStreaming returning error: status={}", error.getStatusCode());
            sendErrorResponse(httpResponse, error);
        } else if (streamingResponse != null) {
            handleCustomSSEResponse(streamingResponse.getPublisher(), httpResponse);
            LOGGER.info("REST sendMessageStreaming SSE stream ended");
        } else {
            sendErrorResponse(httpResponse,
                    restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError("Internal error")));
        }
    }

    public void resubscribeTask(String taskId, HttpServletRequest httpRequest, HttpServletResponse httpResponse, SecurityContext securityContext) throws IOException {
        LOGGER.info("REST resubscribeTask called for taskId={} from {}", taskId, httpRequest.getRemoteAddr());
        ServerCallContext context = createCallContext(httpRequest, securityContext, SUBSCRIBE_TO_TASK_METHOD);
        RestHandler.HTTPRestStreamingResponse streamingResponse = null;
        RestHandler.HTTPRestResponse error = null;
        try {
            // Streaming path: read the tenant from the @RequestScoped TenantHolder, not the servlet request attribute — the attribute is not reliably visible on the RESTEasy SSE async writer thread (see the Task 2 spike); the holder is re-associated on the async thread within the same request.
            RestHandler.HTTPRestResponse response = restHandler.subscribeToTask(context, tenantHolder.getTenant(), taskId);
            if (response instanceof RestHandler.HTTPRestStreamingResponse hTTPRestStreamingResponse) {
                streamingResponse = hTTPRestStreamingResponse;
            } else {
                error = response;
            }
        } catch (Throwable t) {
            LOGGER.error("Internal error while processing streaming request", t);
            error = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError("Internal error"));
        }
        if (error != null) {
            LOGGER.info("REST resubscribeTask returning error for taskId={}: status={}, body={}", taskId, error.getStatusCode(), error.getBody());
            sendErrorResponse(httpResponse, error);
        } else if (streamingResponse != null) {
            handleCustomSSEResponse(streamingResponse.getPublisher(), httpResponse);
            LOGGER.info("REST resubscribeTask SSE stream ended for taskId={}", taskId);
        } else {
            sendErrorResponse(httpResponse,
                    restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError("Internal error")));
        }
    }

    public Response getAgentCard(HttpServletRequest httpRequest) {
        RestHandler.HTTPRestResponse response = restHandler.getAgentCard(readTenant(httpRequest));

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
        ServerCallContext context = createCallContext(httpRequest, securityContext, GET_EXTENDED_AGENT_CARD_METHOD);
        RestHandler.HTTPRestResponse response = restHandler.getExtendedAgentCard(context, readTenant(httpRequest));
        return Response.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .entity(response.getBody())
                .build();
    }

    public Response getExtendedAgentCard(HttpServletRequest httpRequest, SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext, GET_EXTENDED_AGENT_CARD_METHOD);
        RestHandler.HTTPRestResponse response = restHandler.getExtendedAgentCard(context, readTenant(httpRequest));
        return Response.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .entity(response.getBody())
                .build();
    }

    public Response listTasks(HttpServletRequest httpRequest, SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext, LIST_TASK_METHOD);
        RestHandler.HTTPRestResponse response;
        try {
            String contextId = httpRequest.getParameter("contextId");
            String statusStr = httpRequest.getParameter("status");
            String pageSizeStr = httpRequest.getParameter(PAGE_SIZE_PARAM);
            String pageToken = httpRequest.getParameter(PAGE_TOKEN_PARAM);
            String historyLengthStr = httpRequest.getParameter(HISTORY_LENGTH_PARAM);
            String statusTimestampAfter = httpRequest.getParameter(STATUS_TIMESTAMP_AFTER);
            String includeArtifactsStr = httpRequest.getParameter("includeArtifacts");

            response = restHandler.listTasks(context, readTenant(httpRequest), contextId, statusStr, pageSizeStr,
                    pageToken, historyLengthStr, statusTimestampAfter, includeArtifactsStr);
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            LOGGER.error("Internal error while processing request", t);
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError("Internal error"));
        }
        return Response.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .entity(response.getBody())
                .build();
    }

    public Response getTask(String taskId, String historyLengthStr, HttpServletRequest httpRequest, SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext, GET_TASK_METHOD);
        RestHandler.HTTPRestResponse response;
        try {
            response = restHandler.getTask(context, readTenant(httpRequest), taskId, historyLengthStr);
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            LOGGER.error("Internal error while processing request", t);
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError("Internal error"));
        }
        return Response.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .entity(response.getBody())
                .build();
    }

    public Response cancelTask(String taskId, String body, HttpServletRequest httpRequest, SecurityContext securityContext) {
        LOGGER.info("REST cancelTask called for taskId={} from {}", taskId, httpRequest.getRemoteAddr());
        ServerCallContext context = createCallContext(httpRequest, securityContext, CANCEL_TASK_METHOD);
        RestHandler.HTTPRestResponse response;
        try {
            response = restHandler.cancelTask(context, readTenant(httpRequest), body, taskId);
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            LOGGER.error("Internal error while processing request", t);
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError("Internal error"));
        }
        LOGGER.info("REST cancelTask response for taskId={}: status={}, body={}", taskId, response.getStatusCode(), response.getBody());
        return Response.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .entity(response.getBody())
                .build();
    }

    public Response setTaskPushNotificationConfiguration(String taskId, String body, HttpServletRequest httpRequest, SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext, SET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD);
        RestHandler.HTTPRestResponse response;
        try {
            response = restHandler.createTaskPushNotificationConfiguration(context, readTenant(httpRequest), body, taskId);
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            LOGGER.error("Internal error while processing request", t);
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError("Internal error"));
        }
        return Response.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .entity(response.getBody())
                .build();
    }

    public Response getTaskPushNotificationConfiguration(String taskId, String configId, HttpServletRequest httpRequest, SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext, GET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD);
        RestHandler.HTTPRestResponse response;
        try {
            response = restHandler.getTaskPushNotificationConfiguration(context, readTenant(httpRequest), taskId, configId);
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            LOGGER.error("Internal error while processing request", t);
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError("Internal error"));
        }
        return Response.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .entity(response.getBody())
                .build();
    }

    public Response getOrListTaskPushNotificationConfigurations(String taskId, HttpServletRequest httpRequest, SecurityContext securityContext) {
        RestHandler.HTTPRestResponse response;
        try {
            String requestURI = httpRequest.getRequestURI();
            if (requestURI.endsWith("/")) {
                ServerCallContext context = createCallContext(httpRequest, securityContext,
                        GET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD);
                response = restHandler.getTaskPushNotificationConfiguration(context, readTenant(httpRequest),
                        taskId, null);
            } else {
                ServerCallContext context = createCallContext(httpRequest, securityContext,
                        LIST_TASK_PUSH_NOTIFICATION_CONFIG_METHOD);
                String pageSize = httpRequest.getParameter(PAGE_SIZE_PARAM);
                String pageToken = httpRequest.getParameter(PAGE_TOKEN_PARAM);
                response = restHandler.listTaskPushNotificationConfigurations(context, readTenant(httpRequest),
                        taskId, pageSize, pageToken);
            }
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            LOGGER.error("Internal error while processing request", t);
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError("Internal error"));
        }
        return Response.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .entity(response.getBody())
                .build();
    }

    public Response deleteTaskPushNotificationConfiguration(String taskId, String configId, HttpServletRequest httpRequest, SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext, DELETE_TASK_PUSH_NOTIFICATION_CONFIG_METHOD);
        RestHandler.HTTPRestResponse response;
        try {
            response = restHandler.deleteTaskPushNotificationConfiguration(context, readTenant(httpRequest), taskId, configId);
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            LOGGER.error("Internal error while processing request", t);
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError("Internal error"));
        }
        return Response.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .entity(response.getBody())
                .build();
    }

    private void sendErrorResponse(HttpServletResponse httpResponse, RestHandler.HTTPRestResponse error) throws IOException {
        httpResponse.setStatus(error.getStatusCode());
        httpResponse.setHeader(CONTENT_TYPE, error.getContentType());
        httpResponse.getWriter().write(error.getBody());
        httpResponse.getWriter().flush();
    }

    private void handleCustomSSEResponse(Flow.Publisher<String> publisher,
            HttpServletResponse response) throws IOException {
        response.setHeader(CONTENT_TYPE, MediaType.SERVER_SENT_EVENTS);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        CompletableFuture<Void> streamingComplete = new CompletableFuture<>();
        try (PrintWriter writer = response.getWriter()) {
            writer.write(": SSE stream started\n\n");
            writer.flush();
            publisher.subscribe(new SSESubscriber(streamingComplete, writer, heartbeatScheduler));
            streamingComplete.get();
        } catch (Exception e) {
            LOGGER.error("Error waiting for streaming completion: {}", e.getMessage(), e);
            throw new IOException("Streaming failed", e);
        }
    }

    public static void setStreamingIsSubscribedRunnable(Runnable streamingIsSubscribedRunnable) {
        SSESubscriber.setStreamingIsSubscribedRunnable(streamingIsSubscribedRunnable);
    }

    protected ServerCallContext createCallContext(HttpServletRequest request, SecurityContext securityContext) {
        return createCallContext(request, securityContext, null);
    }

    protected ServerCallContext createCallContext(HttpServletRequest request, SecurityContext securityContext, String methodName) {
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
        if (methodName != null) {
            state.put(METHOD_NAME_KEY, methodName);
        }
        state.put(TENANT_KEY, readTenant(request));
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

    private static String readTenant(HttpServletRequest request) {
        Object t = request.getAttribute(A2ARequestAttributes.A2A_TENANT_ATTR);
        return t instanceof String s ? s : "";
    }
}
