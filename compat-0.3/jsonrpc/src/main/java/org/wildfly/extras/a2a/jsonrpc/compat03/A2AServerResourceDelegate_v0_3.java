package org.wildfly.extras.a2a.jsonrpc.compat03;

import static org.a2aproject.sdk.compat03.transport.jsonrpc.context.JSONRPCContextKeys_v0_3.HEADERS_KEY;
import static org.a2aproject.sdk.compat03.transport.jsonrpc.context.JSONRPCContextKeys_v0_3.METHOD_NAME_KEY;

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
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.a2aproject.sdk.compat03.common.A2AHeaders_v0_3;
import org.a2aproject.sdk.compat03.conversion.A2AProtocol_v0_3;
import org.a2aproject.sdk.compat03.json.JsonProcessingException_v0_3;
import org.a2aproject.sdk.compat03.json.JsonUtil_v0_3;
import org.a2aproject.sdk.compat03.spec.CancelTaskRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.DeleteTaskPushNotificationConfigParams_v0_3;
import org.a2aproject.sdk.compat03.spec.DeleteTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.GetAuthenticatedExtendedCardRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.GetTaskPushNotificationConfigParams_v0_3;
import org.a2aproject.sdk.compat03.spec.GetTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.GetTaskRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.InternalError_v0_3;
import org.a2aproject.sdk.compat03.spec.InvalidParamsError_v0_3;
import org.a2aproject.sdk.compat03.spec.InvalidRequestError_v0_3;
import org.a2aproject.sdk.compat03.spec.JSONParseError_v0_3;
import org.a2aproject.sdk.compat03.spec.JSONRPCError_v0_3;
import org.a2aproject.sdk.compat03.spec.JSONRPCErrorResponse_v0_3;
import org.a2aproject.sdk.compat03.spec.JSONRPCMessage_v0_3;
import org.a2aproject.sdk.compat03.spec.JSONRPCResponse_v0_3;
import org.a2aproject.sdk.compat03.spec.ListTaskPushNotificationConfigParams_v0_3;
import org.a2aproject.sdk.compat03.spec.ListTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.MessageSendParams_v0_3;
import org.a2aproject.sdk.compat03.spec.MethodNotFoundError_v0_3;
import org.a2aproject.sdk.compat03.spec.NonStreamingJSONRPCRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.SendMessageRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.SendStreamingMessageRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.SetTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.StreamingJSONRPCRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.TaskIdParams_v0_3;
import org.a2aproject.sdk.compat03.spec.TaskPushNotificationConfig_v0_3;
import org.a2aproject.sdk.compat03.spec.TaskQueryParams_v0_3;
import org.a2aproject.sdk.compat03.spec.TaskResubscriptionRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.UnsupportedOperationError_v0_3;
import org.a2aproject.sdk.compat03.transport.jsonrpc.handler.JSONRPCHandler_v0_3;
import org.a2aproject.sdk.compat03.util.Utils_v0_3;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.server.extensions.A2AExtensions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class A2AServerResourceDelegate_v0_3 {

    private static final Logger LOGGER = LoggerFactory.getLogger(A2AServerResourceDelegate_v0_3.class);

    private final JSONRPCHandler_v0_3 jsonRpcHandler;

    private static volatile Runnable streamingIsSubscribedRunnable;

    public A2AServerResourceDelegate_v0_3(JSONRPCHandler_v0_3 jsonRpcHandler) {
        this.jsonRpcHandler = jsonRpcHandler;
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
            com.google.gson.JsonObject node;
            try {
                node = JsonParser.parseString(body).getAsJsonObject();
            } catch (Exception e) {
                throw new JSONParseError_v0_3(e.getMessage());
            }

            com.google.gson.JsonElement idElement = node.get("id");
            if (idElement != null && !idElement.isJsonNull() && !idElement.isJsonPrimitive()) {
                throw new InvalidRequestError_v0_3("Invalid JSON-RPC request: 'id' must be a string, number, or null");
            }
            if (idElement != null && !idElement.isJsonNull() && idElement.isJsonPrimitive()) {
                com.google.gson.JsonPrimitive idPrimitive = idElement.getAsJsonPrimitive();
                requestId = idPrimitive.isNumber() ? idPrimitive.getAsLong() : idPrimitive.getAsString();
            }

            com.google.gson.JsonElement jsonrpcElement = node.get("jsonrpc");
            if (jsonrpcElement == null || !jsonrpcElement.isJsonPrimitive()
                    || !JSONRPCMessage_v0_3.JSONRPC_VERSION.equals(jsonrpcElement.getAsString())) {
                throw new InvalidRequestError_v0_3("Invalid JSON-RPC request: missing or invalid 'jsonrpc' field");
            }

            com.google.gson.JsonElement methodElement = node.get("method");
            if (methodElement == null || !methodElement.isJsonPrimitive()) {
                throw new InvalidRequestError_v0_3("Invalid JSON-RPC request: missing or invalid 'method' field");
            }

            String methodName = methodElement.getAsString();
            context.getState().put(METHOD_NAME_KEY, methodName);

            NonStreamingJSONRPCRequest_v0_3<?> request = deserializeNonStreamingRequest(node, requestId, methodName);
            response = processNonStreamingRequest(request, context);
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
            com.google.gson.JsonObject node;
            try {
                node = JsonParser.parseString(body).getAsJsonObject();
            } catch (Exception e) {
                throw new JSONParseError_v0_3(e.getMessage());
            }

            com.google.gson.JsonElement idElement = node.get("id");
            if (idElement != null && !idElement.isJsonNull() && !idElement.isJsonPrimitive()) {
                throw new InvalidRequestError_v0_3("Invalid JSON-RPC request: 'id' must be a string, number, or null");
            }
            if (idElement != null && !idElement.isJsonNull() && idElement.isJsonPrimitive()) {
                com.google.gson.JsonPrimitive idPrimitive = idElement.getAsJsonPrimitive();
                requestId = idPrimitive.isNumber() ? idPrimitive.getAsLong() : idPrimitive.getAsString();
            }

            com.google.gson.JsonElement jsonrpcElement = node.get("jsonrpc");
            if (jsonrpcElement == null || !jsonrpcElement.isJsonPrimitive()
                    || !JSONRPCMessage_v0_3.JSONRPC_VERSION.equals(jsonrpcElement.getAsString())) {
                throw new InvalidRequestError_v0_3("Invalid JSON-RPC request: missing or invalid 'jsonrpc' field");
            }

            com.google.gson.JsonElement methodElement = node.get("method");
            if (methodElement == null || !methodElement.isJsonPrimitive()) {
                throw new InvalidRequestError_v0_3("Invalid JSON-RPC request: missing or invalid 'method' field");
            }

            String methodName = methodElement.getAsString();
            context.getState().put(METHOD_NAME_KEY, methodName);

            request = deserializeStreamingRequest(node, requestId, methodName);
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

    private NonStreamingJSONRPCRequest_v0_3<?> deserializeNonStreamingRequest(
            com.google.gson.JsonObject node, Object requestId, String methodName) {
        try {
            return switch (methodName) {
                case GetTaskRequest_v0_3.METHOD -> new GetTaskRequest_v0_3(
                        JSONRPCMessage_v0_3.JSONRPC_VERSION, requestId, methodName, deserializeParams(node, TaskQueryParams_v0_3.class));
                case CancelTaskRequest_v0_3.METHOD -> new CancelTaskRequest_v0_3(
                        JSONRPCMessage_v0_3.JSONRPC_VERSION, requestId, methodName, deserializeParams(node, TaskIdParams_v0_3.class));
                case SendMessageRequest_v0_3.METHOD -> new SendMessageRequest_v0_3(
                        JSONRPCMessage_v0_3.JSONRPC_VERSION, requestId, methodName, deserializeParams(node, MessageSendParams_v0_3.class));
                case SetTaskPushNotificationConfigRequest_v0_3.METHOD -> new SetTaskPushNotificationConfigRequest_v0_3(
                        JSONRPCMessage_v0_3.JSONRPC_VERSION, requestId, methodName, deserializeParams(node, TaskPushNotificationConfig_v0_3.class));
                case GetTaskPushNotificationConfigRequest_v0_3.METHOD -> new GetTaskPushNotificationConfigRequest_v0_3(
                        JSONRPCMessage_v0_3.JSONRPC_VERSION, requestId, methodName, deserializeParams(node, GetTaskPushNotificationConfigParams_v0_3.class));
                case ListTaskPushNotificationConfigRequest_v0_3.METHOD -> new ListTaskPushNotificationConfigRequest_v0_3(
                        JSONRPCMessage_v0_3.JSONRPC_VERSION, requestId, methodName, deserializeParams(node, ListTaskPushNotificationConfigParams_v0_3.class));
                case DeleteTaskPushNotificationConfigRequest_v0_3.METHOD -> new DeleteTaskPushNotificationConfigRequest_v0_3(
                        JSONRPCMessage_v0_3.JSONRPC_VERSION, requestId, methodName, deserializeParams(node, DeleteTaskPushNotificationConfigParams_v0_3.class));
                case GetAuthenticatedExtendedCardRequest_v0_3.METHOD -> new GetAuthenticatedExtendedCardRequest_v0_3(
                        JSONRPCMessage_v0_3.JSONRPC_VERSION, requestId, methodName, null);
                default -> throw new MethodNotFoundError_v0_3();
            };
        } catch (JSONRPCError_v0_3 e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidParamsError_v0_3(e.getMessage());
        }
    }

    private StreamingJSONRPCRequest_v0_3<?> deserializeStreamingRequest(
            com.google.gson.JsonObject node, Object requestId, String methodName) {
        try {
            return switch (methodName) {
                case SendStreamingMessageRequest_v0_3.METHOD -> new SendStreamingMessageRequest_v0_3(
                        JSONRPCMessage_v0_3.JSONRPC_VERSION, requestId, methodName, deserializeParams(node, MessageSendParams_v0_3.class));
                case TaskResubscriptionRequest_v0_3.METHOD -> new TaskResubscriptionRequest_v0_3(
                        JSONRPCMessage_v0_3.JSONRPC_VERSION, requestId, methodName, deserializeParams(node, TaskIdParams_v0_3.class));
                default -> throw new MethodNotFoundError_v0_3();
            };
        } catch (JSONRPCError_v0_3 e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidParamsError_v0_3(e.getMessage());
        }
    }

    private <T> T deserializeParams(com.google.gson.JsonObject node, Class<T> paramsType) throws JsonProcessingException_v0_3 {
        com.google.gson.JsonElement paramsElement = node.get("params");
        if (paramsElement == null || paramsElement.isJsonNull()) {
            return null;
        }
        return JsonUtil_v0_3.fromJson(paramsElement.toString(), paramsType);
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

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                LOGGER.debug("Custom SSE subscriber onSubscribe called");
                this.subscription = subscription;
                subscription.request(1);

                Runnable runnable = streamingIsSubscribedRunnable;
                if (runnable != null) {
                    runnable.run();
                }
            }

            @Override
            public void onNext(JSONRPCResponse_v0_3<?> item) {
                LOGGER.debug("Custom SSE subscriber onNext called with item: {}", item);
                try {
                    long id = eventId.getAndIncrement();
                    String sseEvent = "data: " + Utils_v0_3.toJsonString(item) + "\nid: " + id + "\n\n";

                    writer.write(sseEvent);
                    writer.flush();

                    if (writer.checkError()) {
                        LOGGER.info("SSE write failed (likely client disconnect)");
                        handleClientDisconnect();
                        return;
                    }

                    LOGGER.debug("Custom SSE event sent successfully with id: {}", id);
                    subscription.request(1);
                } catch (Exception e) {
                    LOGGER.error("Error writing SSE event: {}", e.getMessage(), e);
                    onError(e);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                LOGGER.debug("Custom SSE subscriber onError called: {}", throwable.getMessage(), throwable);
                handleClientDisconnect();
                streamingComplete.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                LOGGER.debug("Custom SSE subscriber onComplete called");
                try {
                    writer.close();
                } catch (Exception e) {
                    LOGGER.error("Error closing writer: {}", e.getMessage(), e);
                }
                streamingComplete.complete(null);
            }

            private void handleClientDisconnect() {
                LOGGER.debug("SSE connection closed, calling EventConsumer.cancel() to stop polling loop");
                if (subscription != null) {
                    subscription.cancel();
                }
                context.invokeEventConsumerCancelCallback();
                try {
                    writer.close();
                } catch (Exception e) {
                    LOGGER.debug("Error closing writer during disconnect: {}", e.getMessage());
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

        Enumeration<String> en = request.getHeaders(A2AHeaders_v0_3.X_A2A_EXTENSIONS);
        List<String> extensionHeaderValues = new ArrayList<>();
        while (en.hasMoreElements()) {
            extensionHeaderValues.add(en.nextElement());
        }
        Set<String> requestedExtensions = A2AExtensions.getRequestedExtensions(extensionHeaderValues);

        return new ServerCallContext(user, state, requestedExtensions, A2AProtocol_v0_3.PROTOCOL_VERSION);
    }
}
