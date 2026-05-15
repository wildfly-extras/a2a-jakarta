package org.wildfly.extras.a2a.server.apps.rest;

import static org.a2aproject.sdk.server.ServerCallContext.TRANSPORT_KEY;
import static org.a2aproject.sdk.transport.rest.context.RestContextKeys.HEADERS_KEY;
import static org.a2aproject.sdk.transport.rest.context.RestContextKeys.TENANT_KEY;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

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
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import org.a2aproject.sdk.common.A2AHeaders;
import org.a2aproject.sdk.server.ExtendedAgentCard;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.server.extensions.A2AExtensions;
import org.a2aproject.sdk.server.util.async.Internal;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/")
public class A2ARestServerResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(A2ARestServerResource.class);
    private static final String PAGE_SIZE_PARAM = "pageSize";
    private static final String PAGE_TOKEN_PARAM = "pageToken";
    private static final String HISTORY_LENGTH_PARAM = "historyLength";
    private static final String STATUS_TIMESTAMP_AFTER = "statusTimestampAfter";

    @Inject
    RestHandler jsonRestHandler;

    @Inject
    @ExtendedAgentCard
    Instance<AgentCard> extendedAgentCard;

    // Hook so testing can wait until the async Subscription is subscribed.
    private static volatile Runnable streamingIsSubscribedRunnable;

    @Inject
    @Internal
    Executor executor;

    @Inject
    Instance<CallContextFactory> callContextFactory;

    /**
     * Handles incoming POST requests to the main A2A endpoint.Dispatches the
     * request to the appropriate JSON-RPC handler method and returns the response.
     *
     * @param body
     * @param httpRequest the HTTP request
     * @return the JSON-RPC response which may be an error response
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("message:send")
    public Response sendMessage(String body, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = null;
        try {
            response = jsonRestHandler.sendMessage(context, extractTenant(httpRequest), body);
        } catch (A2AError e) {
            response = jsonRestHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = jsonRestHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return Response.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .entity(response.getBody())
                    .build();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("message:stream")
    public void sendMessageStreaming(String body, @Context HttpServletRequest httpRequest, @Context HttpServletResponse httpResponse, @Context SecurityContext securityContext) throws IOException {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestStreamingResponse streamingResponse = null;
        RestHandler.HTTPRestResponse error = null;
        try {
            RestHandler.HTTPRestResponse response = jsonRestHandler.sendStreamingMessage(context, extractTenant(httpRequest), body);
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

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("tasks/{taskId}:subscribe")
    public void resubscribeTask(@PathParam("taskId") String taskId, @Context HttpServletRequest httpRequest, @Context HttpServletResponse httpResponse, @Context SecurityContext securityContext) throws IOException {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestStreamingResponse streamingResponse = null;
        RestHandler.HTTPRestResponse error = null;
        try {
            RestHandler.HTTPRestResponse response = jsonRestHandler.subscribeToTask(context, extractTenant(httpRequest), taskId);
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

    /**
     * Handles incoming GET requests to the agent card endpoint.
     * Returns the agent card in JSON format with appropriate caching headers.
     *
     * <p>Per A2A specification section 8.6, Agent Card HTTP endpoints SHOULD include:
     * <ul>
     *   <li>Cache-Control header with max-age directive (CARD-CACHE-001)</li>
     *   <li>ETag header for conditional request support (CARD-CACHE-002)</li>
     *   <li>Last-Modified header in RFC 1123 format (CARD-CACHE-003)</li>
     * </ul>
     *
     * @return the agent card with caching headers
     */
    @GET
    @Path(".well-known/agent-card.json")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getAgentCard() {
        RestHandler.HTTPRestResponse response = jsonRestHandler.getAgentCard();

        // Generate ETag based on response body content hash
        String etag = "\"" + Integer.toHexString(response.getBody().hashCode()) + "\"";

        // Set Last-Modified to current time in RFC 1123 format
        // In production, this should reflect actual last modification time
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

    @GET
    @Path("card")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getAuthenticatedExtendedCard(@Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = jsonRestHandler.getExtendedAgentCard(context, extractTenant(httpRequest));
        return Response.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .entity(response.getBody())
                .build();
    }

    @GET
    @Path("extendedAgentCard")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getExtendedAgentCard(@Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = jsonRestHandler.getExtendedAgentCard(context, extractTenant(httpRequest));
        return Response.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .entity(response.getBody())
                .build();
    }

    @GET
    @Path("tasks")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response listTasks(@Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = null;
        try {
            // Extract query parameters
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

            // Parse optional parameters
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

            response = jsonRestHandler.listTasks(context, extractTenant(httpRequest), contextId, statusStr, pageSize,
                    pageToken, historyLength, statusTimestampAfter, includeArtifacts);
        } catch (NumberFormatException e) {
            response = jsonRestHandler.createErrorResponse(new InvalidParamsError("Invalid number format in parameters"));
        } catch (IllegalArgumentException e) {
            response = jsonRestHandler.createErrorResponse(new InvalidParamsError("Invalid parameter value: " + e.getMessage()));
        } catch (A2AError e) {
            response = jsonRestHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = jsonRestHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return Response.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .entity(response.getBody())
                    .build();
        }
    }

    @GET
    @Path("tasks/{taskId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getTask(@PathParam("taskId") String taskId, @QueryParam("historyLength") String historyLengthStr,
            @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = null;
        try {
            Integer historyLength = null;
            if (historyLengthStr != null && !historyLengthStr.isEmpty()) {
                historyLength = Integer.valueOf(historyLengthStr);
            }
            response = jsonRestHandler.getTask(context, extractTenant(httpRequest), taskId, historyLength);
        } catch (NumberFormatException e) {
            response = jsonRestHandler.createErrorResponse(new InvalidParamsError("bad historyLength"));
        } catch (A2AError e) {
            response = jsonRestHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = jsonRestHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return Response.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .entity(response.getBody())
                    .build();
        }
    }

    @POST
    @Path("tasks/{taskId}:cancel")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response cancelTask(@PathParam("taskId") String taskId, String body, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = null;
        try {
            response = jsonRestHandler.cancelTask(context, extractTenant(httpRequest), body, taskId);
        } catch (A2AError e) {
            response = jsonRestHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = jsonRestHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return Response.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .entity(response.getBody())
                    .build();
        }
    }

    @POST
    @Path("tasks/{taskId}/pushNotificationConfigs")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setTaskPushNotificationConfiguration(@PathParam("taskId") String taskId, String body, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = null;
        try {
            response = jsonRestHandler.createTaskPushNotificationConfiguration(context, extractTenant(httpRequest), body, taskId);
        } catch (A2AError e) {
            response = jsonRestHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = jsonRestHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return Response.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .entity(response.getBody())
                    .build();
        }
    }

    @GET
    @Path("tasks/{taskId}/pushNotificationConfigs/{configId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getTaskPushNotificationConfiguration(@PathParam("taskId") String taskId, @PathParam("configId") String configId, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = null;
        try {
            response = jsonRestHandler.getTaskPushNotificationConfiguration(context, extractTenant(httpRequest), taskId, configId);
        } catch (A2AError e) {
            response = jsonRestHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = jsonRestHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return Response.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .entity(response.getBody())
                    .build();
        }
    }

    @GET
    @Path("tasks/{taskId}/pushNotificationConfigs")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getOrListTaskPushNotificationConfigurations(@PathParam("taskId") String taskId, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = null;
        try {
            if (taskId == null || taskId.isEmpty()) {
                response = jsonRestHandler.createErrorResponse(new InvalidParamsError("bad task id"));
            } else {
                // Check if request has trailing slash to distinguish GET (with /) from LIST (without /)
                String requestURI = httpRequest.getRequestURI();
                if (requestURI.endsWith("/")) {
                    // GET with null configId - trailing slash case
                    response = jsonRestHandler.getTaskPushNotificationConfiguration(context, extractTenant(httpRequest),
                            taskId, null);
                } else {
                    // LIST - no trailing slash case
                    int pageSize = 0;
                    if (httpRequest.getParameter(PAGE_SIZE_PARAM) != null) {
                        pageSize = Integer.parseInt(httpRequest.getParameter(PAGE_SIZE_PARAM));
                    }
                    String pageToken = "";
                    if (httpRequest.getParameter(PAGE_TOKEN_PARAM) != null) {
                        pageToken = httpRequest.getParameter(PAGE_TOKEN_PARAM);
                    }
                    response = jsonRestHandler.listTaskPushNotificationConfigurations(context, extractTenant(httpRequest),
                            taskId, pageSize, pageToken);
                }
            }
        } catch (NumberFormatException e) {
            response = jsonRestHandler.createErrorResponse(new InvalidParamsError("bad " + PAGE_SIZE_PARAM));
        } catch (A2AError e) {
            response = jsonRestHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = jsonRestHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        }
        return Response.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .entity(response.getBody())
                .build();
    }

    @DELETE
    @Path("tasks/{taskId}/pushNotificationConfigs/{configId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteTaskPushNotificationConfiguration(@PathParam("taskId") String taskId, @PathParam("configId") String configId, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        ServerCallContext context = createCallContext(httpRequest, securityContext);
        RestHandler.HTTPRestResponse response = null;
        try {
            response = jsonRestHandler.deleteTaskPushNotificationConfiguration(context, extractTenant(httpRequest), taskId, configId);
        } catch (A2AError e) {
            response = jsonRestHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = jsonRestHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
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

    /**
     * Handles the streaming response using custom SSE formatting.
     * This approach avoids JAX-RS SSE compatibility issues with async publishers.
     * Implements proper client disconnect detection and EventConsumer cancellation.
     */
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
        A2ARestServerResource.streamingIsSubscribedRunnable = streamingIsSubscribedRunnable;
        SSESubscriber.setStreamingIsSubscribedRunnable(streamingIsSubscribedRunnable);
    }

    private ServerCallContext createCallContext(HttpServletRequest request, SecurityContext securityContext) {

        if (callContextFactory.isUnsatisfied()) {
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
            // TODO Python's impl has
            //    state['auth'] = request.auth
            //  in jsonrpc_app.py. Figure out what this maps to in what we have here

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
        } else {
            CallContextFactory builder = callContextFactory.get();
            return builder.build(request);
        }
    }

    private String extractTenant(HttpServletRequest request) {
        // Extract tenant from request URI
        // Quarkus uses regex like: ^\\/(?<tenant>[^\\/]*\\/?)message:send$
        // This means tenant is the optional segment BEFORE the known endpoint path
        // Examples:
        //   /message:send -> tenant=""
        //   /tenant1/message:send -> tenant="tenant1"
        //   /tasks -> tenant=""
        //   /tenant1/tasks -> tenant="tenant1"
        String requestURI = request.getRequestURI();
        if (requestURI == null || requestURI.isBlank()) {
            return "";
        }

        // Remove leading slash
        if (requestURI.startsWith("/")) {
            requestURI = requestURI.substring(1);
        }

        // Extract first path segment
        int slashIndex = requestURI.indexOf('/');
        int colonIndex = requestURI.indexOf(':');
        String firstSegment;

        if (colonIndex >= 0 && (slashIndex < 0 || colonIndex < slashIndex)) {
            // Colon before slash (e.g., "message:send")
            firstSegment = requestURI.substring(0, colonIndex);
        } else if (slashIndex > 0) {
            // Slash found, extract up to it
            firstSegment = requestURI.substring(0, slashIndex);
        } else {
            // No slash or colon, entire URI is the first segment
            firstSegment = requestURI;
        }

        // Check if first segment is a known endpoint prefix
        // These match the @Path annotations in this class
        if (firstSegment.equals("message") ||
            firstSegment.equals("tasks") ||
            firstSegment.equals("card") ||
            firstSegment.equals("extendedAgentCard") ||
            firstSegment.equals(".well-known")) {
            // First segment is an endpoint, not a tenant
            return "";
        }

        // First segment is the tenant
        return firstSegment;
    }
}
