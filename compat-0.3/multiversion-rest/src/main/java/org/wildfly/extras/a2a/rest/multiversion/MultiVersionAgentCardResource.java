package org.wildfly.extras.a2a.rest.multiversion;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.wildfly.extras.a2a.rest.A2ARestServerResourceDelegate;

@Path("/")
public class MultiVersionAgentCardResource {

    @Inject
    RestHandler v10Handler;

    private A2ARestServerResourceDelegate delegate;

    private A2ARestServerResourceDelegate getDelegate() {
        if (delegate == null) {
            delegate = new A2ARestServerResourceDelegate(v10Handler);
        }
        return delegate;
    }

    @GET
    @Path(".well-known/agent-card.json")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getAgentCard() {
        return getDelegate().getAgentCard();
    }
}
