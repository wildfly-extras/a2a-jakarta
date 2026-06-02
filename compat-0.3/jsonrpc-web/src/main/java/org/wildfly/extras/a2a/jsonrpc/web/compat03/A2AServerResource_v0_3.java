package org.wildfly.extras.a2a.jsonrpc.web.compat03;

import java.io.IOException;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import org.a2aproject.sdk.compat03.transport.jsonrpc.handler.JSONRPCHandler_v0_3;
import org.wildfly.extras.a2a.jsonrpc.compat03.A2AServerResourceDelegate_v0_3;

@Path("/")
public class A2AServerResource_v0_3 {

    @Inject
    JSONRPCHandler_v0_3 jsonRpcHandler;

    private A2AServerResourceDelegate_v0_3 delegate;

    private A2AServerResourceDelegate_v0_3 getDelegate() {
        if (delegate == null) {
            delegate = new A2AServerResourceDelegate_v0_3(jsonRpcHandler);
        }
        return delegate;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleNonStreamingRequests(
            String body,
            @Context HttpServletRequest httpRequest,
            @Context SecurityContext securityContext) {
        return getDelegate().handleNonStreamingRequests(body, httpRequest, securityContext);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void handleStreamingRequests(
            String body,
            @Context HttpServletResponse response,
            @Context HttpServletRequest httpRequest,
            @Context SecurityContext securityContext) throws IOException {
        getDelegate().handleStreamingRequests(body, response, httpRequest, securityContext);
    }

    @GET
    @Path("/.well-known/agent-card.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAgentCard() {
        return getDelegate().getAgentCard();
    }

    public static void setStreamingIsSubscribedRunnable(Runnable streamingIsSubscribedRunnable) {
        A2AServerResourceDelegate_v0_3.setStreamingIsSubscribedRunnable(streamingIsSubscribedRunnable);
    }
}
