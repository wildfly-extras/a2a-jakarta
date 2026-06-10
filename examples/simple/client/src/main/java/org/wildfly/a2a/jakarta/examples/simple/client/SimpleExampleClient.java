package org.wildfly.a2a.jakarta.examples.simple.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import io.grpc.ManagedChannelBuilder;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransport;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfigBuilder;
import org.a2aproject.sdk.spec.A2AClientError;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TransportProtocol;

public class SimpleExampleClient implements AutoCloseable {
    private final Client client;

    public SimpleExampleClient(String protocol) throws A2AClientError, A2AClientException {
        AgentCard agentCard = A2ACardResolver
                .builder()
                .baseUrl("http://localhost:8080")
                .build()
                .getAgentCard();

        ClientConfig config = new ClientConfig.Builder()
                .setAcceptedOutputModes(List.of("text"))
                .setUseClientPreference(true)
                .build();

        ClientBuilder clientBuilder = Client.builder(agentCard)
                .clientConfig(config);

        TransportProtocol prot = TransportProtocol.fromString(protocol);
        switch (prot) {
            case JSONRPC -> clientBuilder.withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder());
            case HTTP_JSON -> clientBuilder.withTransport(RestTransport.class, new RestTransportConfigBuilder());
            case GRPC -> clientBuilder.withTransport(
                    GrpcTransport.class,
                    new GrpcTransportConfigBuilder().channelFactory(
                            target -> {
                                // Remove http:// or https:// prefix for gRPC
                                return ManagedChannelBuilder.forTarget(target).usePlaintext().build();
                            }));
        }
        client = clientBuilder.build();
    }

    public String sayHello(String name) throws Exception {
        Message message = A2A.toUserMessage(name);

        final CompletableFuture<String> response = new CompletableFuture<>();

        //CompletableFuture
        BiConsumer<ClientEvent, AgentCard> consumer = (event, agentCard) -> {
            if (event instanceof TaskEvent taskEvent) {
                Task task = taskEvent.getTask();
                StringBuilder sb = new StringBuilder();
                if (task.artifacts() != null) {
                    for (Artifact a : task.artifacts()) {
                        for (Part<?> part : a.parts()) {
                            if (part instanceof TextPart textPart) {
                                sb.append(textPart.text());
                            }
                        }
                    }
                }
                response.complete(sb.toString());
            } else {
                response.completeExceptionally(new IllegalStateException("Expected a TaskEvent"));
            }
        };
        List<BiConsumer<ClientEvent, AgentCard>> consumers = new ArrayList<>();


        client.sendMessage(message, Collections.singletonList(consumer), null, null);
        return response.get(10, TimeUnit.SECONDS);
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalStateException("Usage: SimpleExampleClient <protocol> <name>");
        }
        try (SimpleExampleClient client = new SimpleExampleClient(args[0])) {
            String response = client.sayHello(args[1]);
            System.out.println("Agent responds: " + response);
        }
        System.exit(0);
    }
}
