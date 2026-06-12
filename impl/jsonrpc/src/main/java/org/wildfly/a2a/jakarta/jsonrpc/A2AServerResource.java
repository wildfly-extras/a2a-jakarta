package org.wildfly.a2a.jakarta.jsonrpc;

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

import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;

@Path("/a2a_jsonrpc_v1.0")
public class A2AServerResource {

    @Inject
    JSONRPCHandler jsonRpcHandler;

    private A2AServerResourceDelegate delegate;

    private A2AServerResourceDelegate getDelegate() {
        if (delegate == null) {
            delegate = new A2AServerResourceDelegate(jsonRpcHandler);
        }
        return delegate;
    }

    @GET
    @Path(".well-known/agent-card.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAgentCard() {
        return getDelegate().getAgentCard();
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

    public static void setStreamingIsSubscribedRunnable(Runnable streamingIsSubscribedRunnable) {
        A2AServerResourceDelegate.setStreamingIsSubscribedRunnable(streamingIsSubscribedRunnable);
    }
}
