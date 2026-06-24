package org.wildfly.a2a.jakarta.itk;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.a2aproject.sdk.compat03.spec.AgentCapabilities_v0_3;
import org.a2aproject.sdk.compat03.spec.AgentCard_v0_3;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.Compat03Fields;

@ApplicationScoped
public class ItkAgentCardProducer {

    private int getHttpPort() {
        return Integer.parseInt(System.getProperty("jboss.http.port", "8080"));
    }

    private int getGrpcPort() {
        return Integer.parseInt(System.getProperty("jboss.grpc.port", "9555"));
    }

    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        int httpPort = getHttpPort();
        int grpcPort = getGrpcPort();
        String url = "http://127.0.0.1:" + httpPort;
        List<AgentInterface> interfaces = List.of(
                new AgentInterface("JSONRPC", url),
                new AgentInterface("HTTP+JSON", url),
                new AgentInterface("GRPC", "127.0.0.1:" + grpcPort));

        AgentCard.Builder builder = AgentCard.builder()
                .name("ITK Current Agent")
                .description("Java agent using A2A SDK (current source).")
                .version("1.0.0")
                .supportedInterfaces(interfaces)
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .pushNotifications(true)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(AgentSkill.builder()
                        .id("itk_current")
                        .name("ITK Current")
                        .description("Processes ITK instruction traversals")
                        .tags(List.of("itk"))
                        .examples(List.of())
                        .build()));

        Compat03Fields.addCompat03FieldsIfAvailable(builder, interfaces, url, "JSONRPC");

        return builder.build();
    }

    @Produces
    @PublicAgentCard
    public AgentCard_v0_3 agentCard_v0_3() {
        int httpPort = getHttpPort();
        String url = "http://127.0.0.1:" + httpPort;
        return new AgentCard_v0_3.Builder()
                .name("ITK Current Agent")
                .description("Java agent using A2A SDK (current source).")
                .url(url)
                .version("1.0.0")
                .preferredTransport("JSONRPC")
                .capabilities(new AgentCapabilities_v0_3(true, true, false, List.of()))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .build();
    }
}
