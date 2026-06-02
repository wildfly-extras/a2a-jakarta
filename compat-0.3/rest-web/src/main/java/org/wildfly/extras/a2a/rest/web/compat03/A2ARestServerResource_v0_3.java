package org.wildfly.extras.a2a.rest.web.compat03;

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

import org.a2aproject.sdk.compat03.transport.rest.handler.RestHandler_v0_3;
import org.wildfly.extras.a2a.rest.compat03.A2ARestServerResourceDelegate_v0_3;

@Path("/v1")
public class A2ARestServerResource_v0_3 {

    @Inject
    RestHandler_v0_3 restHandler;

    private A2ARestServerResourceDelegate_v0_3 delegate;

    private A2ARestServerResourceDelegate_v0_3 getDelegate() {
        if (delegate == null) {
            delegate = new A2ARestServerResourceDelegate_v0_3(restHandler);
        }
        return delegate;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("message:send")
    public Response sendMessage(String body, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        return getDelegate().sendMessage(body, httpRequest, securityContext);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("message:stream")
    public void sendMessageStreaming(String body, @Context HttpServletRequest httpRequest, @Context HttpServletResponse httpResponse, @Context SecurityContext securityContext) throws IOException {
        getDelegate().sendMessageStreaming(body, httpRequest, httpResponse, securityContext);
    }

    @GET
    @Path("tasks/{taskId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getTask(@PathParam("taskId") String taskId,
            @QueryParam("history_length") String historyLengthSnakeStr,
            @QueryParam("historyLength") String historyLengthCamelStr,
            @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        return getDelegate().getTask(taskId, historyLengthSnakeStr, historyLengthCamelStr, httpRequest, securityContext);
    }

    @POST
    @Path("tasks/{taskId}:cancel")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response cancelTask(@PathParam("taskId") String taskId, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        return getDelegate().cancelTask(taskId, httpRequest, securityContext);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("tasks/{taskId}:subscribe")
    public void resubscribeTask(@PathParam("taskId") String taskId, @Context HttpServletRequest httpRequest, @Context HttpServletResponse httpResponse, @Context SecurityContext securityContext) throws IOException {
        getDelegate().resubscribeTask(taskId, httpRequest, httpResponse, securityContext);
    }

    @GET
    @Path("card")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getAuthenticatedExtendedCard(@Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        return getDelegate().getAuthenticatedExtendedCard(httpRequest, securityContext);
    }

    @POST
    @Path("tasks/{taskId}/pushNotificationConfigs")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setTaskPushNotificationConfiguration(@PathParam("taskId") String taskId, String body, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        return getDelegate().setTaskPushNotificationConfiguration(taskId, body, httpRequest, securityContext);
    }

    @GET
    @Path("tasks/{taskId}/pushNotificationConfigs/{configId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getTaskPushNotificationConfiguration(@PathParam("taskId") String taskId, @PathParam("configId") String configId, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        return getDelegate().getTaskPushNotificationConfiguration(taskId, configId, httpRequest, securityContext);
    }

    @GET
    @Path("tasks/{taskId}/pushNotificationConfigs")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response listTaskPushNotificationConfigurations(@PathParam("taskId") String taskId, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        return getDelegate().listTaskPushNotificationConfigurations(taskId, httpRequest, securityContext);
    }

    @DELETE
    @Path("tasks/{taskId}/pushNotificationConfigs/{configId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteTaskPushNotificationConfiguration(@PathParam("taskId") String taskId, @PathParam("configId") String configId, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        return getDelegate().deleteTaskPushNotificationConfiguration(taskId, configId, httpRequest, securityContext);
    }

    public static void setStreamingIsSubscribedRunnable(Runnable streamingIsSubscribedRunnable) {
        A2ARestServerResourceDelegate_v0_3.setStreamingIsSubscribedRunnable(streamingIsSubscribedRunnable);
    }
}
