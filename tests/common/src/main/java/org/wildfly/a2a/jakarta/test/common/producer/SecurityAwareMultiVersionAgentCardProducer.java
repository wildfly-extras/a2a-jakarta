package org.wildfly.a2a.jakarta.test.common.producer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.a2aproject.sdk.server.ExtendedAgentCard;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Compat03Fields;
import org.a2aproject.sdk.spec.HTTPAuthSecurityScheme;
import org.a2aproject.sdk.spec.SecurityRequirement;
import org.a2aproject.sdk.spec.TransportProtocol;

@ApplicationScoped
public class SecurityAwareMultiVersionAgentCardProducer {

    private static final String PREFERRED_TRANSPORT = "preferred-transport";
    private static final String A2A_REQUESTHANDLER_TEST_PROPERTIES = "/a2a-requesthandler-test.properties";
    private static final String BASIC_AUTH_SCHEME_NAME = "basicAuth";
    public static final String DEFAULT_TEST_PORT = "8080";
    private static final Set<String> VALID_TRANSPORTS = Set.of(
            TransportProtocol.JSONRPC.asString(),
            TransportProtocol.GRPC.asString(),
            TransportProtocol.HTTP_JSON.asString());

    @Produces
    @PublicAgentCard
    @ExtendedAgentCard
    public AgentCard agentCard() {
        String port = System.getProperty("test.agent.card.port", DEFAULT_TEST_PORT);
        String preferredTransport = loadPreferredTransportFromProperties();
        String transportUrl = "http://localhost:" + port;

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
                .supportedInterfaces(interfaces)
                .securitySchemes(Map.of(
                        BASIC_AUTH_SCHEME_NAME,
                        new HTTPAuthSecurityScheme(null, "basic", "HTTP Basic authentication")))
                .securityRequirements(List.of(
                        SecurityRequirement.builder()
                                .scheme(BASIC_AUTH_SCHEME_NAME, List.of())
                                .build()));

        Compat03Fields.addCompat03FieldsIfAvailable(builder, interfaces, transportUrl, preferredTransport);

        return builder.build();
    }

    private static String loadPreferredTransportFromProperties() {
        URL url = SecurityAwareMultiVersionAgentCardProducer.class.getResource(A2A_REQUESTHANDLER_TEST_PROPERTIES);
        if (url == null) {
            throw new IllegalStateException(A2A_REQUESTHANDLER_TEST_PROPERTIES
                    + " not found on classpath. Each test module must provide this file.");
        }
        Properties properties = new Properties();
        try {
            try (InputStream in = url.openStream()) {
                properties.load(in);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + A2A_REQUESTHANDLER_TEST_PROPERTIES, e);
        }
        String transport = properties.getProperty(PREFERRED_TRANSPORT);
        if (transport == null || transport.isBlank()) {
            throw new IllegalStateException("Property '" + PREFERRED_TRANSPORT
                    + "' is missing or blank in " + A2A_REQUESTHANDLER_TEST_PROPERTIES);
        }
        if (!VALID_TRANSPORTS.contains(transport)) {
            throw new IllegalStateException("Property '" + PREFERRED_TRANSPORT
                    + "' has invalid value '" + transport + "' in " + A2A_REQUESTHANDLER_TEST_PROPERTIES
                    + ". Valid values are: " + VALID_TRANSPORTS);
        }
        return transport;
    }
}
