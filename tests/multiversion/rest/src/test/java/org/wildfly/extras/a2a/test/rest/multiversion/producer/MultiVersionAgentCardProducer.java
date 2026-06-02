package org.wildfly.extras.a2a.test.rest.multiversion.producer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.a2aproject.sdk.server.ExtendedAgentCard;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Compat03Fields;

@ApplicationScoped
public class MultiVersionAgentCardProducer {

    private static final String PREFERRED_TRANSPORT = "preferred-transport";
    private static final String A2A_REQUESTHANDLER_TEST_PROPERTIES = "/a2a-requesthandler-test.properties";

    @Produces
    @PublicAgentCard
    @ExtendedAgentCard
    public AgentCard agentCard() {
        String port = System.getProperty("test.agent.card.port", "8080");
        String preferredTransport = loadPreferredTransportFromProperties();
        String transportUrl = "http://localhost:" + port + "/v1";

        List<AgentInterface> interfaces = Collections.singletonList(new AgentInterface(preferredTransport, transportUrl));

        AgentCard.Builder builder = AgentCard.builder()
                .name("test-card")
                .description("A test agent card")
                .version("1.0")
                .documentationUrl("http://example.com/docs")
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .pushNotifications(true)
                        .extendedAgentCard(true)
                        .build())
                .defaultInputModes(Collections.singletonList("text"))
                .defaultOutputModes(Collections.singletonList("text"))
                .skills(new ArrayList<>())
                .supportedInterfaces(interfaces);

        Compat03Fields.addCompat03FieldsIfAvailable(builder, interfaces, transportUrl, preferredTransport);

        return builder.build();
    }

    private static String loadPreferredTransportFromProperties() {
        URL url = MultiVersionAgentCardProducer.class.getResource(A2A_REQUESTHANDLER_TEST_PROPERTIES);
        if (url == null) {
            return null;
        }
        Properties properties = new Properties();
        try {
            try (InputStream in = url.openStream()) {
                properties.load(in);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return properties.getProperty(PREFERRED_TRANSPORT);
    }
}
