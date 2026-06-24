package org.wildfly.a2a.jakarta.test.grpc.auth;

import static org.wildfly.a2a.jakarta.test.common.ArchiveUtils.getJarForClass;

import org.wildfly.a2a.jakarta.test.common.auth.GrpcCallContextHelper;
import org.wildfly.a2a.jakarta.test.common.auth.MultiUserBasicAuthGrpcInterceptor;
import org.wildfly.a2a.jakarta.test.common.auth.TestCallContextFactory;
import org.wildfly.a2a.jakarta.test.grpc.A2ATestResource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.google.api.AnnotationsProto;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.protobuf.util.JsonFormat;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import mutiny.zero.ZeroPublisher;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransport;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransportProvider;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.auth.AuthInterceptor;
import org.a2aproject.sdk.grpc.A2AServiceGrpc;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.integrations.microprofile.MicroProfileConfigProvider;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.apps.common.AbstractA2AServerTest;
import org.a2aproject.sdk.server.apps.common.AbstractA2AServerWithTaskAuthorizationTest;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.HTTPAuthSecurityScheme;
import org.a2aproject.sdk.spec.SecurityRequirement;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.grpc.handler.GrpcHandler;
import org.a2aproject.sdk.util.Assert;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.AfterAll;
import org.wildfly.a2a.jakarta.common.AsyncManagedExecutorServiceProducer;
import org.wildfly.a2a.jakarta.grpc.WildFlyGrpcHandler;

@ArquillianTest
@RunAsClient
public class WildFlyA2AGrpcWithTaskAuthorizationTestCase extends AbstractA2AServerWithTaskAuthorizationTest {

    private static final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol.GRPC.asString();
    }

    @Override
    protected String getTransportUrl() {
        return "localhost:9555";
    }

    @Override
    protected void configureTransportWithCredentials(ClientBuilder builder, String username, String password) {
        AuthInterceptor authInterceptor = new AuthInterceptor(
                (schemeName, context) -> BASIC_AUTH_SCHEME_NAME.equals(schemeName)
                        ? getEncodedCredentials(username, password) : null);
        builder.withTransport(GrpcTransport.class, new GrpcTransportConfigBuilder()
                .channelFactory(target -> {
                    ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
                    channels.put(username, channel);
                    return channel;
                })
                .addInterceptor(authInterceptor));
    }

    @Deployment
    public static WebArchive createDeployment() throws Exception {
        final JavaArchive[] libraries = List.of(
                getJarForClass(WildFlyGrpcHandler.class),
                getJarForClass(A2A.class),
                getJarForClass(Assert.class),
                getJarForClass(A2AHttpClient.class),
                getJarForClass(PublicAgentCard.class),
                getJarForClass(Event.class),
                getJarForClass(JSONRPCUtils.class),
                getJarForClass(GrpcHandler.class),
                getJarForClass(JsonUtil.class),
                getJarForClass(Gson.class),
                getJarForClass(com.google.protobuf.Message.class),
                getJarForClass(JsonFormat.class),
                getJarForClass(AnnotationsProto.class),
                getJarForClass(ImmutableSet.class),
                getJarForClass(MicroProfileConfigProvider.class),
                getJarForClass(A2AServiceGrpc.class),
                getJarForClass(ZeroPublisher.class),
                getJarForClass(ClientTransport.class),
                getJarForClass(GrpcTransportProvider.class),
                getJarForClass(AsyncManagedExecutorServiceProducer.class)).toArray(new JavaArchive[0]);

        String manifest = "Manifest-Version: 1.0\n" +
                "Dependencies: io.grpc-all\n";

        return ShrinkWrap.create(WebArchive.class, "ROOT.war")
                .addAsLibraries(libraries)
                .addPackage(AbstractA2AServerTest.class.getPackage())
                .addPackage(A2ATestResource.class.getPackage())
                .addClass(GrpcCallContextHelper.class)
                .addClass(MultiUserBasicAuthGrpcInterceptor.class)
                .addClass(TestCallContextFactory.class)
                .addAsWebInfResource("WEB-INF/web.xml")
                .addAsWebInfResource("META-INF/beans.xml", "beans.xml")
                .addAsResource("a2a-requesthandler-test.properties")
                .addAsResource("META-INF/auth-microprofile-config.properties",
                        "META-INF/microprofile-config.properties")
                .setManifest(new StringAsset(manifest));
    }

    @Override
    protected AgentCard fetchAgentCardFromServer() {
        return AgentCard.builder()
                .name("test-card")
                .description("A test agent card")
                .version("1.0")
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface(getTransportProtocol(), getTransportUrl())))
                .securitySchemes(Map.of(
                        BASIC_AUTH_SCHEME_NAME,
                        HTTPAuthSecurityScheme.builder()
                                .scheme("basic")
                                .description("HTTP Basic authentication")
                                .build()))
                .securityRequirements(List.of(new SecurityRequirement(Map.of(BASIC_AUTH_SCHEME_NAME, List.of()))))
                .build();
    }

    @AfterAll
    static void closeChannels() {
        channels.values().forEach(ch -> {
            ch.shutdownNow();
            try {
                ch.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
