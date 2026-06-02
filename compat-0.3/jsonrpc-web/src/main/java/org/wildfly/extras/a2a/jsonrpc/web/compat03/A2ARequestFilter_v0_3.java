package org.wildfly.extras.a2a.jsonrpc.web.compat03;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;

import org.a2aproject.sdk.compat03.spec.CancelTaskRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.DeleteTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.GetAuthenticatedExtendedCardRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.GetTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.GetTaskRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.ListTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.SendMessageRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.SendStreamingMessageRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.SetTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.TaskResubscriptionRequest_v0_3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
@PreMatching
public class A2ARequestFilter_v0_3 implements ContainerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(A2ARequestFilter_v0_3.class);

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
        if (path.equals("/")) {
            return requestContext.getMethod().equals("POST") && requestContext.hasEntity();
        }
        return false;
    }

    private static boolean isStreamingRequest(String requestBody) {
        return requestBody.contains(SendStreamingMessageRequest_v0_3.METHOD) ||
                requestBody.contains(TaskResubscriptionRequest_v0_3.METHOD);
    }

    private static boolean isNonStreamingRequest(String requestBody) {
        return requestBody.contains(SendMessageRequest_v0_3.METHOD) ||
                requestBody.contains(GetTaskRequest_v0_3.METHOD) ||
                requestBody.contains(CancelTaskRequest_v0_3.METHOD) ||
                requestBody.contains(SetTaskPushNotificationConfigRequest_v0_3.METHOD) ||
                requestBody.contains(GetTaskPushNotificationConfigRequest_v0_3.METHOD) ||
                requestBody.contains(ListTaskPushNotificationConfigRequest_v0_3.METHOD) ||
                requestBody.contains(DeleteTaskPushNotificationConfigRequest_v0_3.METHOD) ||
                requestBody.contains(GetAuthenticatedExtendedCardRequest_v0_3.METHOD);
    }

    private static void putAcceptHeader(ContainerRequestContext requestContext, String mediaType) {
        requestContext.getHeaders().putSingle("Accept", mediaType);
    }

}
