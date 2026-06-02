package org.wildfly.extras.a2a.rest.web;

import java.io.IOException;
import java.util.concurrent.Executor;

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

import org.a2aproject.sdk.server.ExtendedAgentCard;
import org.a2aproject.sdk.server.util.async.Internal;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.wildfly.extras.a2a.rest.A2ARestServerResourceDelegate;

@Path("/")
public class A2ARestServerResource {

    @Inject
    RestHandler restHandler;

    @Inject
    @ExtendedAgentCard
    Instance<AgentCard> extendedAgentCard;

    @Inject
    @Internal
    Executor executor;

    private A2ARestServerResourceDelegate delegate;

    private A2ARestServerResourceDelegate getDelegate() {
        if (delegate == null) {
            delegate = new A2ARestServerResourceDelegate(restHandler);
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

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("tasks/{taskId}:subscribe")
    public void resubscribeTask(@PathParam("taskId") String taskId, @Context HttpServletRequest httpRequest, @Context HttpServletResponse httpResponse, @Context SecurityContext securityContext) throws IOException {
        getDelegate().resubscribeTask(taskId, httpRequest, httpResponse, securityContext);
    }

    @GET
    @Path(".well-known/agent-card.json")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getAgentCard() {
        return getDelegate().getAgentCard();
    }

    @GET
    @Path("card")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getAuthenticatedExtendedCard(@Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        return getDelegate().getAuthenticatedExtendedCard(httpRequest, securityContext);
    }

    @GET
    @Path("extendedAgentCard")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getExtendedAgentCard(@Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        return getDelegate().getExtendedAgentCard(httpRequest, securityContext);
    }

    @GET
    @Path("tasks")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response listTasks(@Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        return getDelegate().listTasks(httpRequest, securityContext);
    }

    @GET
    @Path("tasks/{taskId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getTask(@PathParam("taskId") String taskId, @QueryParam("historyLength") String historyLengthStr,
            @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        return getDelegate().getTask(taskId, historyLengthStr, httpRequest, securityContext);
    }

    @POST
    @Path("tasks/{taskId}:cancel")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response cancelTask(@PathParam("taskId") String taskId, String body, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        return getDelegate().cancelTask(taskId, body, httpRequest, securityContext);
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
    public Response getOrListTaskPushNotificationConfigurations(@PathParam("taskId") String taskId, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        return getDelegate().getOrListTaskPushNotificationConfigurations(taskId, httpRequest, securityContext);
    }

    @DELETE
    @Path("tasks/{taskId}/pushNotificationConfigs/{configId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteTaskPushNotificationConfiguration(@PathParam("taskId") String taskId, @PathParam("configId") String configId, @Context HttpServletRequest httpRequest, @Context SecurityContext securityContext) {
        return getDelegate().deleteTaskPushNotificationConfiguration(taskId, configId, httpRequest, securityContext);
    }

    public static void setStreamingIsSubscribedRunnable(Runnable streamingIsSubscribedRunnable) {
        A2ARestServerResourceDelegate.setStreamingIsSubscribedRunnable(streamingIsSubscribedRunnable);
    }
}
