package org.wildfly.extras.a2a.rest.web.compat03;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import org.a2aproject.sdk.compat03.transport.rest.handler.RestHandler_v0_3;
import org.wildfly.extras.a2a.rest.compat03.A2ARestServerResourceDelegate_v0_3;

@Path("/.well-known/agent-card.json")
public class AgentCardResource_v0_3 {

    @Inject
    RestHandler_v0_3 restHandler;

    private A2ARestServerResourceDelegate_v0_3 delegate;

    private A2ARestServerResourceDelegate_v0_3 getDelegate() {
        if (delegate == null) {
            delegate = new A2ARestServerResourceDelegate_v0_3(restHandler);
        }
        return delegate;
    }

    @GET
    public Response getAgentCard() {
        return getDelegate().getAgentCard();
    }
}
