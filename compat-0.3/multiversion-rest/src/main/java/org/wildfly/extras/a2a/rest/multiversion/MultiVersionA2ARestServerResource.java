package org.wildfly.extras.a2a.rest.multiversion;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;

import java.io.IOException;

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
import org.a2aproject.sdk.compat03.transport.rest.handler.RestHandler_v0_3;
import org.a2aproject.sdk.spec.A2AErrorCodes;
import org.a2aproject.sdk.spec.VersionNotSupportedError;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.wildfly.extras.a2a.rest.A2ARestServerResourceDelegate;
import org.wildfly.extras.a2a.rest.compat03.A2ARestServerResourceDelegate_v0_3;

@Path("/v1")
public class MultiVersionA2ARestServerResource {

    private static final String VERSION_1_0 = "1.0";
    private static final String VERSION_0_3 = "0.3";

    @Inject
    RestHandler v10Handler;

    @Inject
    RestHandler_v0_3 v03Handler;

    private A2ARestServerResourceDelegate v10Delegate;
    private A2ARestServerResourceDelegate_v0_3 v03Delegate;

    private A2ARestServerResourceDelegate getV10Delegate() {
        if (v10Delegate == null) {
            v10Delegate = new A2ARestServerResourceDelegate(v10Handler);
        }
        return v10Delegate;
    }

    private A2ARestServerResourceDelegate_v0_3 getV03Delegate() {
        if (v03Delegate == null) {
            v03Delegate = new A2ARestServerResourceDelegate_v0_3(v03Handler);
        }
        return v03Delegate;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("message:send")
    public Response sendMessage(String body, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        String version = resolveVersion(httpRequest);
        if (isV10(version)) {
            return getV10Delegate().sendMessage(body, httpRequest, securityContext);
        } else if (isV03(version)) {
            return getV03Delegate().sendMessage(body, httpRequest, securityContext);
        }
        return createVersionNotSupportedResponse(version);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("message:stream")
    public void sendMessageStreaming(String body, @Context HttpServletRequest httpRequest, @Context HttpServletResponse httpResponse, @Context SecurityContext securityContext) throws IOException {
        String version = resolveVersion(httpRequest);
        if (isV10(version)) {
            getV10Delegate().sendMessageStreaming(body, httpRequest, httpResponse, securityContext);
        } else if (isV03(version)) {
            getV03Delegate().sendMessageStreaming(body, httpRequest, httpResponse, securityContext);
        } else {
            sendVersionNotSupportedError(version, httpResponse);
        }
    }

    @GET
    @Path("tasks/{taskId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getTask(@PathParam("taskId") String taskId,
            @QueryParam("historyLength") String historyLengthStr,
            @QueryParam("history_length") String historyLengthSnakeStr,
            @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        String version = resolveVersion(httpRequest);
        if (isV10(version)) {
            return getV10Delegate().getTask(taskId, historyLengthStr, httpRequest, securityContext);
        } else if (isV03(version)) {
            return getV03Delegate().getTask(taskId, historyLengthSnakeStr, historyLengthStr, httpRequest, securityContext);
        }
        return createVersionNotSupportedResponse(version);
    }

    @POST
    @Path("tasks/{taskId}:cancel")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response cancelTask(@PathParam("taskId") String taskId, String body, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        String version = resolveVersion(httpRequest);
        if (isV10(version)) {
            return getV10Delegate().cancelTask(taskId, body, httpRequest, securityContext);
        } else if (isV03(version)) {
            return getV03Delegate().cancelTask(taskId, httpRequest, securityContext);
        }
        return createVersionNotSupportedResponse(version);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("tasks/{taskId}:subscribe")
    public void resubscribeTask(@PathParam("taskId") String taskId, @Context HttpServletRequest httpRequest, @Context HttpServletResponse httpResponse, @Context SecurityContext securityContext) throws IOException {
        String version = resolveVersion(httpRequest);
        if (isV10(version)) {
            getV10Delegate().resubscribeTask(taskId, httpRequest, httpResponse, securityContext);
        } else if (isV03(version)) {
            getV03Delegate().resubscribeTask(taskId, httpRequest, httpResponse, securityContext);
        } else {
            sendVersionNotSupportedError(version, httpResponse);
        }
    }

    @POST
    @Path("tasks/{taskId}/pushNotificationConfigs")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createTaskPushNotificationConfiguration(@PathParam("taskId") String taskId, String body, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        String version = resolveVersion(httpRequest);
        if (isV10(version)) {
            return getV10Delegate().setTaskPushNotificationConfiguration(taskId, body, httpRequest, securityContext);
        } else if (isV03(version)) {
            return getV03Delegate().setTaskPushNotificationConfiguration(taskId, body, httpRequest, securityContext);
        }
        return createVersionNotSupportedResponse(version);
    }

    @GET
    @Path("tasks/{taskId}/pushNotificationConfigs/{configId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getTaskPushNotificationConfiguration(@PathParam("taskId") String taskId, @PathParam("configId") String configId, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        String version = resolveVersion(httpRequest);
        if (isV10(version)) {
            return getV10Delegate().getTaskPushNotificationConfiguration(taskId, configId, httpRequest, securityContext);
        } else if (isV03(version)) {
            return getV03Delegate().getTaskPushNotificationConfiguration(taskId, configId, httpRequest, securityContext);
        }
        return createVersionNotSupportedResponse(version);
    }

    @GET
    @Path("tasks/{taskId}/pushNotificationConfigs")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response listTaskPushNotificationConfigurations(@PathParam("taskId") String taskId, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        String version = resolveVersion(httpRequest);
        if (isV10(version)) {
            return getV10Delegate().getOrListTaskPushNotificationConfigurations(taskId, httpRequest, securityContext);
        } else if (isV03(version)) {
            return getV03Delegate().listTaskPushNotificationConfigurations(taskId, httpRequest, securityContext);
        }
        return createVersionNotSupportedResponse(version);
    }

    @DELETE
    @Path("tasks/{taskId}/pushNotificationConfigs/{configId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteTaskPushNotificationConfiguration(@PathParam("taskId") String taskId, @PathParam("configId") String configId, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        String version = resolveVersion(httpRequest);
        if (isV10(version)) {
            return getV10Delegate().deleteTaskPushNotificationConfiguration(taskId, configId, httpRequest, securityContext);
        } else if (isV03(version)) {
            return getV03Delegate().deleteTaskPushNotificationConfiguration(taskId, configId, httpRequest, securityContext);
        }
        return createVersionNotSupportedResponse(version);
    }

    @GET
    @Path("card")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getAuthenticatedExtendedCard(@Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        String version = resolveVersion(httpRequest);
        if (isV03(version)) {
            return getV03Delegate().getAuthenticatedExtendedCard(httpRequest, securityContext);
        }
        return createVersionNotSupportedResponse(version);
    }

    @GET
    @Path("extendedAgentCard")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getExtendedAgentCard(@Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        String version = resolveVersion(httpRequest);
        if (isV10(version)) {
            return getV10Delegate().getExtendedAgentCard(httpRequest, securityContext);
        }
        return createVersionNotSupportedResponse(version);
    }

    @GET
    @Path("tasks")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response listTasks(@Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        String version = resolveVersion(httpRequest);
        if (isV10(version)) {
            return getV10Delegate().listTasks(httpRequest, securityContext);
        }
        return createVersionNotSupportedResponse(version);
    }

    private String resolveVersion(HttpServletRequest request) {
        String version = request.getHeader(A2AHeaders.A2A_VERSION);
        if (version == null || version.isBlank()) {
            return VERSION_0_3;
        }
        return version.trim();
    }

    private static boolean isV10(String version) {
        return VERSION_1_0.equals(version);
    }

    private static boolean isV03(String version) {
        return VERSION_0_3.equals(version);
    }

    private Response createVersionNotSupportedResponse(String version) {
        VersionNotSupportedError error = new VersionNotSupportedError(
                null,
                "Protocol version '" + version + "' is not supported. Supported versions: [1.0, 0.3]",
                null);
        A2AErrorCodes errorCode = A2AErrorCodes.fromCode(error.getCode());
        int httpStatus = errorCode != null ? errorCode.httpCode() : 400;
        String body = "{\"error\":{\"code\":" + error.getCode() + ",\"message\":\"" + error.getMessage() + "\"}}";
        return Response.status(httpStatus)
                .header(CONTENT_TYPE, "application/json")
                .entity(body)
                .build();
    }

    private void sendVersionNotSupportedError(String version, HttpServletResponse httpResponse) throws IOException {
        Response r = createVersionNotSupportedResponse(version);
        httpResponse.setStatus(r.getStatus());
        httpResponse.setHeader(CONTENT_TYPE, "application/json");
        httpResponse.getWriter().write((String) r.getEntity());
        httpResponse.getWriter().flush();
    }

    public static void setStreamingIsSubscribedRunnable(Runnable streamingIsSubscribedRunnable) {
        A2ARestServerResourceDelegate.setStreamingIsSubscribedRunnable(streamingIsSubscribedRunnable);
        A2ARestServerResourceDelegate_v0_3.setStreamingIsSubscribedRunnable(streamingIsSubscribedRunnable);
    }
}
