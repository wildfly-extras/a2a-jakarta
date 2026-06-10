package org.wildfly.a2a.server.jakarta.tck;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.a2aproject.sdk.compat03.spec.AgentCapabilities_v0_3;
import org.a2aproject.sdk.compat03.spec.AgentCard_v0_3;
import org.a2aproject.sdk.compat03.spec.AgentSkill_v0_3;
import org.a2aproject.sdk.server.PublicAgentCard;

@ApplicationScoped
public class StubAgentCardProducer_v0_3 {

    @Produces
    @PublicAgentCard
    public AgentCard_v0_3 createStubAgentCard() {
        return new AgentCard_v0_3.Builder()
                .name("stub")
                .description("Stub agent card for multi-version testing")
                .url("http://localhost:9999")
                .version("0.0.0")
                .capabilities(new AgentCapabilities_v0_3.Builder().build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(new AgentSkill_v0_3.Builder()
                        .id("stub")
                        .name("stub")
                        .description("stub")
                        .tags(List.of())
                        .build()))
                .build();
    }
}
