package org.wildfly.a2a.jakarta.jsonrpc.compat03;

import static org.a2aproject.sdk.compat03.transport.jsonrpc.context.JSONRPCContextKeys_v0_3.HEADERS_KEY;
import static org.a2aproject.sdk.compat03.transport.jsonrpc.context.JSONRPCContextKeys_v0_3.METHOD_NAME_KEY;
import static org.a2aproject.sdk.server.ServerCallContext.TRANSPORT_KEY;

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
import org.a2aproject.sdk.compat03.common.A2AHeaders_v0_3;
import org.a2aproject.sdk.compat03.conversion.A2AProtocol_v0_3;
import org.a2aproject.sdk.compat03.json.JsonProcessingException_v0_3;
import org.a2aproject.sdk.compat03.json.JsonUtil_v0_3;
import org.a2aproject.sdk.compat03.spec.CancelTaskRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.DeleteTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.GetAuthenticatedExtendedCardRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.GetTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.GetTaskRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.InternalError_v0_3;
import org.a2aproject.sdk.compat03.spec.JSONParseError_v0_3;
import org.a2aproject.sdk.compat03.spec.JSONRPCErrorResponse_v0_3;
import org.a2aproject.sdk.compat03.spec.JSONRPCError_v0_3;
import org.a2aproject.sdk.compat03.spec.JSONRPCResponse_v0_3;
import org.a2aproject.sdk.compat03.spec.ListTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.MethodNotFoundError_v0_3;
import org.a2aproject.sdk.compat03.spec.NonStreamingJSONRPCRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.SendMessageRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.SendStreamingMessageRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.SetTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.StreamingJSONRPCRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.TaskResubscriptionRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.UnsupportedOperationError_v0_3;
import org.a2aproject.sdk.compat03.transport.jsonrpc.handler.JSONRPCHandler_v0_3;
import org.a2aproject.sdk.compat03.transport.jsonrpc.handler.JSONRPCRequestDecoder_v0_3;
import org.a2aproject.sdk.compat03.util.Utils_v0_3;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.server.extensions.A2AExtensions;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class A2AServerResourceDelegate_v0_3 {

    private static final Logger LOGGER = LoggerFactory.getLogger(A2AServerResourceDelegate_v0_3.class);
    private static final long HEARTBEAT_INTERVAL_MS = 1000;

    private final JSONRPCHandler_v0_3 jsonRpcHandler;
    private final ScheduledExecutorService heartbeatScheduler;

    private static volatile Runnable streamingIsSubscribedRunnable;

    public A2AServerResourceDelegate_v0_3(JSONRPCHandler_v0_3 jsonRpcHandler, ScheduledExecutorService heartbeatScheduler) {
        this.jsonRpcHandler = jsonRpcHandler;
        this.heartbeatScheduler = heartbeatScheduler;
    }

    public Response handleNonStreamingRequests(
            String body,
            HttpServletRequest httpRequest,
            SecurityContext securityContext) {

        ServerCallContext context = createCallContext(httpRequest, securityContext);
        LOGGER.debug("Handling non-streaming request");
        JSONRPCResponse_v0_3<?> response = null;
        JSONRPCErrorResponse_v0_3 error = null;
        Object requestId = null;
        try {
            JSONRPCRequestDecoder_v0_3.DecodeResult result = JSONRPCRequestDecoder_v0_3.decode(body);
            if (result instanceof JSONRPCRequestDecoder_v0_3.DecodeFailure failure) {
                requestId = failure.requestId();
                if (failure.methodName() != null) {
                    context.getState().put(METHOD_NAME_KEY, failure.methodName());
                }
                throw failure.error();
            }
            JSONRPCRequestDecoder_v0_3.DecodedRequest decoded = (JSONRPCRequestDecoder_v0_3.DecodedRequest) result;
            requestId = decoded.requestId();
            context.getState().put(METHOD_NAME_KEY, decoded.methodName());
            if (decoded.request() instanceof NonStreamingJSONRPCRequest_v0_3<?> request) {
                response = processNonStreamingRequest(request, context);
            } else {
                throw new MethodNotFoundError_v0_3();
            }
        } catch (JSONRPCError_v0_3 e) {
            error = new JSONRPCErrorResponse_v0_3(requestId, e);
        } catch (JsonSyntaxException e) {
            error = new JSONRPCErrorResponse_v0_3(requestId, new JSONParseError_v0_3(e.getMessage()));
        } catch (Throwable t) {
            LOGGER.error("Unexpected error processing request: {}", t.getMessage(), t);
            error = new JSONRPCErrorResponse_v0_3(requestId, new InternalError_v0_3(t.getMessage()));
        }

        String serialized;
        if (error != null) {
            serialized = Utils_v0_3.toJsonString(error);
        } else {
            serialized = Utils_v0_3.toJsonString(response);
        }

        String contentType = MediaType.APPLICATION_JSON;

        return Response.status(Response.Status.OK)
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .entity(serialized)
                .build();
    }

    public void handleStreamingRequests(
            String body,
            HttpServletResponse httpResponse,
            HttpServletRequest httpRequest,
            SecurityContext securityContext) throws IOException {

        ServerCallContext context = createCallContext(httpRequest, securityContext);
        LOGGER.debug("Handling streaming request with custom SSE response");

        Object requestId = null;
        StreamingJSONRPCRequest_v0_3<?> request = null;
        try {
            JSONRPCRequestDecoder_v0_3.DecodeResult result = JSONRPCRequestDecoder_v0_3.decode(body);
            if (result instanceof JSONRPCRequestDecoder_v0_3.DecodeFailure failure) {
                requestId = failure.requestId();
                if (failure.methodName() != null) {
                    context.getState().put(METHOD_NAME_KEY, failure.methodName());
                }
                throw failure.error();
            }
            JSONRPCRequestDecoder_v0_3.DecodedRequest decoded = (JSONRPCRequestDecoder_v0_3.DecodedRequest) result;
            requestId = decoded.requestId();
            context.getState().put(METHOD_NAME_KEY, decoded.methodName());
            if (decoded.request() instanceof StreamingJSONRPCRequest_v0_3<?> streamingRequest) {
                request = streamingRequest;
            } else {
                throw new MethodNotFoundError_v0_3();
            }
        } catch (JSONRPCError_v0_3 e) {
            LOGGER.debug("Error validating streaming request: {}", e.getMessage());
            sendJsonRpcError(httpResponse, requestId, e);
            return;
        } catch (JsonSyntaxException e) {
            LOGGER.warn("JSON syntax error in streaming request: {}", e.getMessage());
            sendJsonRpcError(httpResponse, requestId, new JSONParseError_v0_3(e.getMessage()));
            return;
        } catch (Throwable t) {
            LOGGER.error("Unexpected error processing streaming request: {}", t.getMessage(), t);
            sendJsonRpcError(httpResponse, requestId, new InternalError_v0_3(t.getMessage()));
            return;
        }

        httpResponse.setContentType(MediaType.SERVER_SENT_EVENTS);
        httpResponse.setCharacterEncoding("UTF-8");
        httpResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");

        try {
            Flow.Publisher<? extends JSONRPCResponse_v0_3<?>> publisher = createStreamingPublisher(request, context);
            LOGGER.debug("Created streaming publisher: {}", publisher);

            if (publisher != null) {
                LOGGER.debug("Handling custom SSE response for publisher: {}", publisher);
                handleCustomSSEResponse(publisher, httpResponse, context);
            } else {
                LOGGER.debug("Unsupported streaming request type: {}", request.getClass().getSimpleName());
                httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported streaming request type");
            }
        } catch (JSONRPCError_v0_3 e) {
            LOGGER.debug("Error in streaming request: {}", e.getMessage());
            sendErrorSSE(httpResponse, requestId, e);
        } catch (Throwable e) {
            LOGGER.error("Unexpected error processing streaming request: {}", e.getMessage(), e);
            sendErrorSSE(httpResponse, requestId, new InternalError_v0_3(e.getMessage()));
        }

        LOGGER.debug("Completed streaming request processing");
    }

    public Response getAgentCard() {
        try {
            String agentCard = JsonUtil_v0_3.toJson(jsonRpcHandler.getAgentCard());
            return Response.ok(agentCard)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .build();
        } catch (JsonProcessingException_v0_3 e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Internal Server Error")
                    .build();
        }
    }

    private JSONRPCResponse_v0_3<?> processNonStreamingRequest(
            NonStreamingJSONRPCRequest_v0_3<?> request, ServerCallContext context) {
        if (request instanceof GetTaskRequest_v0_3 req) {
            return jsonRpcHandler.onGetTask(req, context);
        } else if (request instanceof CancelTaskRequest_v0_3 req) {
            return jsonRpcHandler.onCancelTask(req, context);
        } else if (request instanceof SetTaskPushNotificationConfigRequest_v0_3 req) {
            return jsonRpcHandler.setPushNotificationConfig(req, context);
        } else if (request instanceof GetTaskPushNotificationConfigRequest_v0_3 req) {
            return jsonRpcHandler.getPushNotificationConfig(req, context);
        } else if (request instanceof SendMessageRequest_v0_3 req) {
            return jsonRpcHandler.onMessageSend(req, context);
        } else if (request instanceof ListTaskPushNotificationConfigRequest_v0_3 req) {
            return jsonRpcHandler.listPushNotificationConfig(req, context);
        } else if (request instanceof DeleteTaskPushNotificationConfigRequest_v0_3 req) {
            return jsonRpcHandler.deletePushNotificationConfig(req, context);
        } else if (request instanceof GetAuthenticatedExtendedCardRequest_v0_3 req) {
            return jsonRpcHandler.onGetAuthenticatedExtendedCardRequest(req, context);
        } else {
            return generateErrorResponse(request, new UnsupportedOperationError_v0_3());
        }
    }

    private Flow.Publisher<? extends JSONRPCResponse_v0_3<?>> createStreamingPublisher(
            StreamingJSONRPCRequest_v0_3<?> request, ServerCallContext context) {
        if (request instanceof SendStreamingMessageRequest_v0_3 req) {
            return jsonRpcHandler.onMessageSendStream(req, context);
        } else if (request instanceof TaskResubscriptionRequest_v0_3 req) {
            return jsonRpcHandler.onResubscribeToTask(req, context);
        } else {
            return null;
        }
    }

    private void handleCustomSSEResponse(Flow.Publisher<? extends JSONRPCResponse_v0_3<?>> publisher,
                                         HttpServletResponse response,
                                         ServerCallContext context) throws IOException {

        PrintWriter writer = response.getWriter();
        AtomicLong eventId = new AtomicLong(0);
        CompletableFuture<Void> streamingComplete = new CompletableFuture<>();

        publisher.subscribe(new Flow.Subscriber<JSONRPCResponse_v0_3<?>>() {
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
            public void onNext(JSONRPCResponse_v0_3<?> item) {
                if (disconnected) {
                    LOGGER.debug("SSE onNext: ignoring event (already disconnected)");
                    return;
                }
                LOGGER.debug("Custom SSE subscriber onNext called with item: {}", item);
                try {
                    long id = eventId.getAndIncrement();
                    String sseEvent = "data: " + Utils_v0_3.toJsonString(item) + "\nid: " + id + "\n\n";

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

    private JSONRPCResponse_v0_3<?> generateErrorResponse(org.a2aproject.sdk.compat03.spec.JSONRPCRequest_v0_3<?> request, JSONRPCError_v0_3 error) {
        return new JSONRPCErrorResponse_v0_3(request.getId(), error);
    }

    private void sendJsonRpcError(HttpServletResponse response, Object id, JSONRPCError_v0_3 error) {
        try {
            JSONRPCErrorResponse_v0_3 errorResponse = new JSONRPCErrorResponse_v0_3(id, error);
            String jsonData = Utils_v0_3.toJsonString(errorResponse);
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON);
            response.getWriter().write(jsonData);
            response.getWriter().flush();
        } catch (Exception e) {
            LOGGER.error("Error sending JSON-RPC error response: {}", e.getMessage(), e);
        }
    }

    private void sendErrorSSE(HttpServletResponse response, Object id, JSONRPCError_v0_3 error) {
        try {
            PrintWriter writer = response.getWriter();
            JSONRPCErrorResponse_v0_3 errorResponse = new JSONRPCErrorResponse_v0_3(id, error);
            String jsonData = Utils_v0_3.toJsonString(errorResponse);
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
        A2AServerResourceDelegate_v0_3.streamingIsSubscribedRunnable = streamingIsSubscribedRunnable;
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
        state.put(TRANSPORT_KEY, TransportProtocol.JSONRPC);

        Enumeration<String> en = request.getHeaders(A2AHeaders_v0_3.X_A2A_EXTENSIONS);
        List<String> extensionHeaderValues = new ArrayList<>();
        while (en.hasMoreElements()) {
            extensionHeaderValues.add(en.nextElement());
        }
        Set<String> requestedExtensions = A2AExtensions.getRequestedExtensions(extensionHeaderValues);

        return new ServerCallContext(user, state, requestedExtensions, A2AProtocol_v0_3.PROTOCOL_VERSION);
    }
}
