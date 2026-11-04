package org.wildfly.a2a.jakarta.jsonrpc;

import static org.a2aproject.sdk.server.ServerCallContext.TRANSPORT_KEY;
import static org.a2aproject.sdk.transport.jsonrpc.context.JSONRPCContextKeys.HEADERS_KEY;
import static org.a2aproject.sdk.transport.jsonrpc.context.JSONRPCContextKeys.METHOD_NAME_KEY;
import static org.a2aproject.sdk.transport.jsonrpc.context.JSONRPCContextKeys.TENANT_KEY;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import com.google.gson.JsonSyntaxException;
import org.a2aproject.sdk.common.A2AHeaders;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.jsonrpc.common.json.IdJsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.json.InvalidParamsJsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.jsonrpc.common.json.MethodNotFoundJsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2AErrorResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2ARequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2AResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CreateTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.DeleteTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetExtendedAgentCardRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTaskPushNotificationConfigsRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.NonStreamingJSONRPCRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.StreamingJSONRPCRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SubscribeToTaskRequest;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.server.extensions.A2AExtensions;
import org.a2aproject.sdk.server.multitenancy.TenantNotFoundException;
import org.a2aproject.sdk.server.util.sse.SseFormatter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.InvalidRequestError;
import org.a2aproject.sdk.spec.JSONParseError;
import org.a2aproject.sdk.spec.MethodNotFoundError;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.spec.UnsupportedOperationError;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class A2AServerResourceDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(A2AServerResourceDelegate.class);

    private static final long HEARTBEAT_INTERVAL_MS = 1000;

    private final JSONRPCHandler jsonRpcHandler;
    private final ScheduledExecutorService heartbeatScheduler;

    private static volatile Runnable streamingIsSubscribedRunnable;

    public A2AServerResourceDelegate(JSONRPCHandler jsonRpcHandler, ScheduledExecutorService heartbeatScheduler) {
        this.jsonRpcHandler = jsonRpcHandler;
        this.heartbeatScheduler = heartbeatScheduler;
    }

    public Response handleNonStreamingRequests(
            String body,
            HttpServletRequest httpRequest,
            SecurityContext securityContext) {

        ServerCallContext context = createCallContext(httpRequest, securityContext);
        LOGGER.debug("Handling non-streaming request");
        A2AResponse<?> response;
        try {
            A2ARequest<?> request = JSONRPCUtils.parseRequestBody(body, readTenant(httpRequest));
            context.getState().put(METHOD_NAME_KEY, request.getMethod());
            response = processNonStreamingRequest((NonStreamingJSONRPCRequest<?>) request, context);
        } catch (InvalidParamsJsonMappingException e) {
            LOGGER.warn("Invalid params in request: {}", e.getMessage());
            response = new A2AErrorResponse(e.getId(), new InvalidParamsError(null, e.getMessage(), null));
        } catch (MethodNotFoundJsonMappingException e) {
            LOGGER.warn("Method not found in request: {}", e.getMessage());
            response = new A2AErrorResponse(e.getId(), new MethodNotFoundError(null, e.getMessage(), null));
        } catch (IdJsonMappingException e) {
            LOGGER.warn("Invalid request ID: {}", e.getMessage());
            response = new A2AErrorResponse(e.getId(), new InvalidRequestError(null, e.getMessage(), null));
        } catch (JsonMappingException e) {
            LOGGER.warn("JSON mapping error: {}", e.getMessage(), e);
            response = new A2AErrorResponse(new InvalidRequestError(null, e.getMessage(), null));
        } catch (JsonSyntaxException e) {
            LOGGER.warn("JSON syntax error: {}", e.getMessage());
            response = new A2AErrorResponse(new JSONParseError(e.getMessage()));
        } catch (JsonProcessingException e) {
            LOGGER.warn("JSON processing error: {}", e.getMessage());
            response = new A2AErrorResponse(new JSONParseError(e.getMessage()));
        } catch (Throwable t) {
            LOGGER.error("Unexpected error processing request: {}", t.getMessage(), t);
            response = new A2AErrorResponse(new InternalError("Internal error"));
        }

        String serialized = serializeResponse(response);
        String contentType = org.a2aproject.sdk.common.MediaType.APPLICATION_JSON;

        return Response.status(Response.Status.OK)
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .entity(serialized)
                .build();
    }

    public void handleStreamingRequests(
            String body,
            HttpServletResponse response,
            HttpServletRequest httpRequest,
            SecurityContext securityContext) throws IOException {

        ServerCallContext context = createCallContext(httpRequest, securityContext);
        LOGGER.debug("Handling streaming request with custom SSE response");

        A2ARequest<?> request = null;
        Flow.Publisher<? extends A2AResponse<?>> publisher;
        try {
            request = JSONRPCUtils.parseRequestBody(body, readTenant(httpRequest));
            context.getState().put(METHOD_NAME_KEY, request.getMethod());
            publisher = createStreamingPublisher((StreamingJSONRPCRequest<?>) request, context);
        } catch (A2AError e) {
            LOGGER.debug("A2AError validating streaming request: {}", e.getMessage());
            sendJsonRpcError(response, request != null ? request.getId() : null, e);
            return;
        } catch (InvalidParamsJsonMappingException e) {
            LOGGER.warn("Invalid params in streaming request: {}", e.getMessage());
            sendJsonRpcError(response, e.getId(), new InvalidParamsError(null, e.getMessage(), null));
            return;
        } catch (MethodNotFoundJsonMappingException e) {
            LOGGER.warn("Method not found in streaming request: {}", e.getMessage());
            sendJsonRpcError(response, e.getId(), new MethodNotFoundError(null, e.getMessage(), null));
            return;
        } catch (IdJsonMappingException e) {
            LOGGER.warn("Invalid request ID in streaming request: {}", e.getMessage());
            sendJsonRpcError(response, e.getId(), new InvalidRequestError(null, e.getMessage(), null));
            return;
        } catch (JsonMappingException e) {
            LOGGER.warn("JSON mapping error in streaming request: {}", e.getMessage(), e);
            sendJsonRpcError(response, null, new InvalidRequestError(null, e.getMessage(), null));
            return;
        } catch (JsonSyntaxException e) {
            LOGGER.warn("JSON syntax error in streaming request: {}", e.getMessage());
            sendJsonRpcError(response, null, new JSONParseError(e.getMessage()));
            return;
        } catch (JsonProcessingException e) {
            LOGGER.warn("JSON processing error in streaming request: {}", e.getMessage());
            sendJsonRpcError(response, null, new JSONParseError(e.getMessage()));
            return;
        } catch (Throwable e) {
            LOGGER.error("Unexpected error processing streaming request: {}", e.getMessage(), e);
            sendJsonRpcError(response, null, new InternalError("Internal error"));
            return;
        }

        response.setContentType(MediaType.SERVER_SENT_EVENTS);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");

        try {
            LOGGER.debug("Created streaming publisher: {}", publisher);

            if (publisher != null) {
                LOGGER.debug("Handling custom SSE response for publisher: {}", publisher);
                handleCustomSSEResponse(publisher, response, context);
            } else {
                LOGGER.debug("Unsupported streaming request type: {}", request.getClass().getSimpleName());
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported streaming request type");
            }
        } catch (A2AError e) {
            LOGGER.debug("A2AError in streaming request: {}", e.getMessage());
            sendErrorSSE(response, request.getId(), e);
        } catch (Throwable e) {
            LOGGER.error("Unexpected error processing streaming request: {}", e.getMessage(), e);
            sendErrorSSE(response, null, new InternalError("Internal error"));
        }

        LOGGER.debug("Completed streaming request processing");
    }

    public Response getAgentCard(HttpServletRequest httpRequest) {
        AgentCard agentCard;
        try {
            agentCard = jsonRpcHandler.getAgentCard(readTenant(httpRequest));
        } catch (TenantNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(e.getMessage())
                    .build();
        }

        String etag = "\"" + Integer.toHexString(agentCard.hashCode()) + "\"";

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("GMT"));
        String lastModified = now.format(DateTimeFormatter.RFC_1123_DATE_TIME);

        String agentCardJson;
        try {
            agentCardJson = JsonUtil.toJson(agentCard);
        } catch (JsonProcessingException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Internal Server Error")
                    .build();
        }

        return Response.ok(agentCardJson)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .header(HttpHeaders.ETAG, etag)
                .header("Last-Modified", lastModified)
                .build();
    }

    private A2AResponse<?> processNonStreamingRequest(NonStreamingJSONRPCRequest<?> request,
                                                          ServerCallContext context) {
        if (request instanceof GetTaskRequest req) {
            return jsonRpcHandler.onGetTask(req, context);
        } else if (request instanceof CancelTaskRequest req) {
            return jsonRpcHandler.onCancelTask(req, context);
        } else if (request instanceof ListTasksRequest req) {
            return jsonRpcHandler.onListTasks(req, context);
        } else if (request instanceof CreateTaskPushNotificationConfigRequest req) {
            return jsonRpcHandler.setPushNotificationConfig(req, context);
        } else if (request instanceof GetTaskPushNotificationConfigRequest req) {
            return jsonRpcHandler.getPushNotificationConfig(req, context);
        } else if (request instanceof SendMessageRequest req) {
            return jsonRpcHandler.onMessageSend(req, context);
        } else if (request instanceof ListTaskPushNotificationConfigsRequest req) {
            return jsonRpcHandler.listPushNotificationConfigs(req, context);
        } else if (request instanceof DeleteTaskPushNotificationConfigRequest req) {
            return jsonRpcHandler.deletePushNotificationConfig(req, context);
        } else if (request instanceof GetExtendedAgentCardRequest req) {
            return jsonRpcHandler.onGetExtendedCardRequest(req, context);
        } else {
            return generateErrorResponse(request, new UnsupportedOperationError());
        }
    }

    private Flow.Publisher<? extends A2AResponse<?>> createStreamingPublisher(StreamingJSONRPCRequest<?> request,
                                                                                 ServerCallContext context) {
        if (request instanceof SendStreamingMessageRequest req) {
            return jsonRpcHandler.onMessageSendStream(req, context);
        } else if (request instanceof SubscribeToTaskRequest req) {
            return jsonRpcHandler.onSubscribeToTask(req, context);
        } else {
            return null;
        }
    }

    private void handleCustomSSEResponse(Flow.Publisher<? extends A2AResponse<?>> publisher,
                                       HttpServletResponse response,
                                       ServerCallContext context) throws IOException {

        PrintWriter writer = response.getWriter();
        AtomicLong eventId = new AtomicLong(0);
        CompletableFuture<Void> streamingComplete = new CompletableFuture<>();

        publisher.subscribe(new Flow.Subscriber<A2AResponse<?>>() {
            private Flow.Subscription subscription;
            private volatile boolean disconnected;
            private ScheduledFuture<?> heartbeatFuture;
            // Serializes all access to the writer between the EventConsumer thread (onNext) and the
            // heartbeat thread. The servlet container's PrintWriter is not guaranteed to be thread-safe:
            // Undertow's ServletPrintWriterDelegate bypasses PrintWriter's own lock, so unsynchronized
            // concurrent writes corrupt the HTTP chunked encoding of the response.
            private final Object writeLock = new Object();
            // Guarded by writeLock. Once set, no further writes are attempted.
            private boolean closed;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                LOGGER.debug("Custom SSE subscriber onSubscribe called");
                this.subscription = subscription;
                // Schedule heartbeat BEFORE requesting events: request() may trigger
                // synchronous onComplete, which calls stopWriting() — the future
                // must already be assigned so it can be cancelled.
                heartbeatFuture = heartbeatScheduler.scheduleAtFixedRate(
                        this::heartbeat, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
                // Request all events upfront (mirrors the a2a-java reference SseResponseWriter, see
                // a2a-java #906): a single-item demand window drops back-to-back emissions from the
                // EventConsumer. EventConsumer.BUFFER_FLUSH_DELAY_MS guarantees the final write is
                // flushed before onComplete fires, so write-level backpressure is not needed.
                subscription.request(Long.MAX_VALUE);

                Runnable runnable = streamingIsSubscribedRunnable;
                if (runnable != null) {
                    runnable.run();
                }
            }

            @Override
            public void onNext(A2AResponse<?> item) {
                if (disconnected) {
                    LOGGER.debug("SSE onNext: ignoring event (already disconnected)");
                    return;
                }
                LOGGER.debug("Custom SSE subscriber onNext called with item: {}", item);
                try {
                    long id = eventId.getAndIncrement();
                    String sseEvent = SseFormatter.formatResponseAsSSE(item, id);

                    boolean failed;
                    synchronized (writeLock) {
                        if (closed) {
                            return;
                        }
                        writer.write(sseEvent);
                        writer.flush();
                        failed = writer.checkError();
                    }

                    if (failed) {
                        LOGGER.info("SSE write failed (likely client disconnect)");
                        handleClientDisconnect();
                        return;
                    }

                    LOGGER.debug("Custom SSE event sent successfully with id: {}", id);
                } catch (Exception e) {
                    LOGGER.error("Error writing SSE event: {}", e.getMessage(), e);
                    onError(e);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                LOGGER.debug("Custom SSE subscriber onError called: {}", throwable.getMessage(), throwable);
                stopWriting();
                handleClientDisconnect();
                streamingComplete.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                LOGGER.debug("Custom SSE subscriber onComplete called");
                stopWriting();
                synchronized (writeLock) {
                    try {
                        writer.close();
                    } catch (Exception e) {
                        LOGGER.error("Error closing writer: {}", e.getMessage(), e);
                    }
                }
                streamingComplete.complete(null);
            }

            private void handleClientDisconnect() {
                if (disconnected) {
                    return;
                }
                disconnected = true;
                stopWriting();
                LOGGER.debug("SSE connection closed, calling EventConsumer.cancel() to stop polling loop");
                if (subscription != null) {
                    subscription.cancel();
                }
                context.invokeEventConsumerCancelCallback();
                synchronized (writeLock) {
                    try {
                        writer.close();
                    } catch (Exception e) {
                        LOGGER.debug("Error closing writer during disconnect: {}", e.getMessage());
                    }
                }
            }

            /**
             * Cancels the heartbeat and stops all further writes. Once this returns, no write is in
             * progress and none will start. Cancelling the heartbeat future alone is not enough, as
             * {@code cancel(false)} does not wait for a running heartbeat.
             */
            private void stopWriting() {
                if (heartbeatFuture != null) {
                    heartbeatFuture.cancel(false);
                }
                synchronized (writeLock) {
                    closed = true;
                }
            }

            private void heartbeat() {
                if (disconnected) {
                    return;
                }
                boolean failed;
                synchronized (writeLock) {
                    if (closed) {
                        return;
                    }
                    writer.write(": ping\n\n");
                    writer.flush();
                    failed = writer.checkError();
                }
                if (failed) {
                    LOGGER.info("SSE heartbeat detected client disconnect");
                    handleClientDisconnect();
                }
            }
        });

        try {
            streamingComplete.get();
        } catch (Exception e) {
            LOGGER.error("Error waiting for streaming completion: {}", e.getMessage(), e);
            throw new IOException("Streaming failed", e);
        }
    }

    private A2AResponse<?> generateErrorResponse(A2ARequest<?> request, A2AError error) {
        return new A2AErrorResponse(request.getId(), error);
    }

    private void sendJsonRpcError(HttpServletResponse response, Object id, A2AError error) {
        try {
            A2AErrorResponse errorResponse = new A2AErrorResponse(id, error);
            String jsonData = serializeResponse(errorResponse);
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(org.a2aproject.sdk.common.MediaType.APPLICATION_JSON);
            response.getWriter().write(jsonData);
            response.getWriter().flush();
        } catch (Exception e) {
            LOGGER.error("Error sending JSON-RPC error response: {}", e.getMessage(), e);
        }
    }

    private void sendErrorSSE(HttpServletResponse response, Object id, A2AError error) {
        try {
            PrintWriter writer = response.getWriter();
            A2AErrorResponse errorResponse = new A2AErrorResponse(id, error);
            String jsonData = serializeResponse(errorResponse);
            writer.write("data: " + jsonData + "\n");
            writer.write("id: 0\n");
            writer.write("\n");
            writer.flush();
            writer.close();
        } catch (Exception e) {
            LOGGER.error("Error sending SSE error response: {}", e.getMessage(), e);
        }
    }

    public static void setStreamingIsSubscribedRunnable(Runnable streamingIsSubscribedRunnable) {
        A2AServerResourceDelegate.streamingIsSubscribedRunnable = streamingIsSubscribedRunnable;
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
        for (Enumeration<String> headerNames = request.getHeaderNames(); headerNames.hasMoreElements() ; ) {
            String name = headerNames.nextElement();
            headers.put(name, request.getHeader(name));
        }

        state.put(HEADERS_KEY, headers);

        Enumeration<String> en = request.getHeaders(A2AHeaders.A2A_EXTENSIONS);
        List<String> extensionHeaderValues = new ArrayList<>();
        while (en.hasMoreElements()) {
            extensionHeaderValues.add(en.nextElement());
        }
        Set<String> requestedExtensions = A2AExtensions.getRequestedExtensions(extensionHeaderValues);
        state.put(TENANT_KEY, readTenant(request));
        state.put(TRANSPORT_KEY, TransportProtocol.JSONRPC);

        String requestedVersion = request.getHeader(A2AHeaders.A2A_VERSION);
        return new ServerCallContext(user, state, requestedExtensions, requestedVersion);
    }

    private static String readTenant(HttpServletRequest request) {
        Object t = request.getAttribute(org.wildfly.a2a.jakarta.common.A2ARequestAttributes.A2A_TENANT_ATTR);
        return t instanceof String s ? s : "";
    }

    static String serializeResponse(A2AResponse<?> response) {
        if (response instanceof A2AErrorResponse error) {
            return JSONRPCUtils.toJsonRPCErrorResponse(error.getId(), error.getError());
        }
        if (response.getError() != null) {
            return JSONRPCUtils.toJsonRPCErrorResponse(response.getId(), response.getError());
        }
        com.google.protobuf.MessageOrBuilder protoMessage = convertToProto(response);
        return JSONRPCUtils.toJsonRPCResultResponse(response.getId(), protoMessage);
    }

    private static com.google.protobuf.MessageOrBuilder convertToProto(A2AResponse<?> response) {
        if (response instanceof org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskResponse r) {
            return ProtoUtils.ToProto.task(r.getResult());
        } else if (response instanceof org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskResponse r) {
            return ProtoUtils.ToProto.task(r.getResult());
        } else if (response instanceof org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageResponse r) {
            return ProtoUtils.ToProto.taskOrMessage(r.getResult());
        } else if (response instanceof org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResponse r) {
            return ProtoUtils.ToProto.listTasksResult(r.getResult());
        } else if (response instanceof org.a2aproject.sdk.jsonrpc.common.wrappers.CreateTaskPushNotificationConfigResponse r) {
            return ProtoUtils.ToProto.createTaskPushNotificationConfigResponse(r.getResult());
        } else if (response instanceof org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskPushNotificationConfigResponse r) {
            return ProtoUtils.ToProto.getTaskPushNotificationConfigResponse(r.getResult());
        } else if (response instanceof org.a2aproject.sdk.jsonrpc.common.wrappers.ListTaskPushNotificationConfigsResponse r) {
            return ProtoUtils.ToProto.listTaskPushNotificationConfigsResponse(r.getResult());
        } else if (response instanceof org.a2aproject.sdk.jsonrpc.common.wrappers.DeleteTaskPushNotificationConfigResponse) {
            return com.google.protobuf.Empty.getDefaultInstance();
        } else if (response instanceof org.a2aproject.sdk.jsonrpc.common.wrappers.GetExtendedAgentCardResponse r) {
            return ProtoUtils.ToProto.getExtendedCardResponse(r.getResult());
        } else if (response instanceof org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageResponse r) {
            return ProtoUtils.ToProto.taskOrMessageStream(r.getResult());
        } else {
            throw new IllegalArgumentException("Unknown response type: " + response.getClass().getName());
        }
    }
}
