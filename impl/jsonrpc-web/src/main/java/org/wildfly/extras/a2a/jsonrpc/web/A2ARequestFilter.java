package org.wildfly.extras.a2a.jsonrpc.web;


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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;

import org.a2aproject.sdk.spec.A2AMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
@PreMatching
public class A2ARequestFilter implements ContainerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(A2ARequestFilter.class);

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (isA2ARequest(requestContext)) {
            try (InputStream entityInputStream = requestContext.getEntityStream()) {
                byte[] requestBodyBytes = entityInputStream.readAllBytes();
                String requestBody = new String(requestBodyBytes);
                // ensure the request is treated as a streaming request or a non-streaming request
                // based on the method in the request body
                if (isStreamingRequest(requestBody)) {
                    LOGGER.debug("Handling request as streaming: {}", requestBody);
                    putAcceptHeader(requestContext, MediaType.SERVER_SENT_EVENTS);
                } else if (isNonStreamingRequest(requestBody)) {
                    LOGGER.debug("Handling request as non-streaming: {}", requestBody);
                    putAcceptHeader(requestContext, MediaType.APPLICATION_JSON);
                }
                // reset the entity stream
                requestContext.setEntityStream(new ByteArrayInputStream(requestBodyBytes));
            } catch(IOException e){
                throw new RuntimeException("Unable to read the request body");
            }
        }
    }

    private boolean isA2ARequest(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath().trim();
        if (path.equals("/") || path.startsWith("/agent/") || path.startsWith("/.well-known/")) {
            return requestContext.getMethod().equals("POST") && requestContext.hasEntity();
        }
        return false;
    }

    private static boolean isStreamingRequest(String requestBody) {
        return requestBody.contains(SEND_STREAMING_MESSAGE_METHOD) ||
                requestBody.contains(A2AMethods.SUBSCRIBE_TO_TASK_METHOD);
    }

    private static boolean isNonStreamingRequest(String requestBody) {
        return requestBody.contains(GET_TASK_METHOD) ||
                requestBody.contains(CANCEL_TASK_METHOD) ||
                requestBody.contains(SEND_MESSAGE_METHOD) ||
                requestBody.contains(LIST_TASK_METHOD) ||
                requestBody.contains(SET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD) ||
                requestBody.contains(GET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD) ||
                requestBody.contains(LIST_TASK_PUSH_NOTIFICATION_CONFIG_METHOD) ||
                requestBody.contains(DELETE_TASK_PUSH_NOTIFICATION_CONFIG_METHOD) ||
                requestBody.contains(GET_EXTENDED_AGENT_CARD_METHOD);
    }

    private static void putAcceptHeader(ContainerRequestContext requestContext, String mediaType) {
        requestContext.getHeaders().putSingle("Accept", mediaType);
    }

}