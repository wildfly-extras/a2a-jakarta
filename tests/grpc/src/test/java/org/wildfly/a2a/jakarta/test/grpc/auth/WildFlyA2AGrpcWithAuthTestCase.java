package org.wildfly.a2a.jakarta.test.grpc.auth;

import org.wildfly.a2a.jakarta.test.grpc.A2ATestResource;

import java.io.File;
import java.util.List;
import java.util.Map;
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
import org.a2aproject.sdk.server.apps.common.AbstractA2AServerWithAuthTest;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.wildfly.a2a.jakarta.common.AsyncManagedExecutorServiceProducer;
import org.wildfly.a2a.jakarta.grpc.WildFlyGrpcHandler;
import org.wildfly.a2a.jakarta.test.common.BasicAuthGrpcInterceptor;

@ArquillianTest
@RunAsClient
public class WildFlyA2AGrpcWithAuthTestCase extends AbstractA2AServerWithAuthTest {

    private static ManagedChannel authenticatedChannel;
    private static ManagedChannel unauthenticatedChannel;

    public WildFlyA2AGrpcWithAuthTestCase() {
        super(8080);
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol.GRPC.asString();
    }

    @Override
    protected String getTransportUrl() {
        return "localhost:9555";
    }

    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(GrpcTransport.class, new GrpcTransportConfigBuilder().channelFactory(target -> {
            unauthenticatedChannel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
            return unauthenticatedChannel;
        }));
    }

    @Override
    protected void configureTransportWithAuth(ClientBuilder builder) {
        AuthInterceptor authInterceptor = new AuthInterceptor(
                (schemeName, context) ->
                        BASIC_AUTH_SCHEME_NAME.equals(schemeName) ? getEncodedCredentials() : null);
        builder.withTransport(GrpcTransport.class, new GrpcTransportConfigBuilder()
                .channelFactory(target -> {
                    authenticatedChannel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
                    return authenticatedChannel;
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
                .addClass(BasicAuthGrpcInterceptor.class)
                .addAsWebInfResource("WEB-INF/web.xml")
                .addAsWebInfResource("META-INF/beans.xml", "beans.xml")
                .addAsResource("a2a-requesthandler-test.properties")
                .addAsResource("META-INF/auth-microprofile-config.properties",
                        "META-INF/microprofile-config.properties")
                .setManifest(new StringAsset(manifest));
    }

    static JavaArchive getJarForClass(Class<?> clazz) throws Exception {
        File f = new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
        return ShrinkWrap.createFromZipFile(JavaArchive.class, f);
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
    public static void closeChannels() {
        if (authenticatedChannel != null) {
            authenticatedChannel.shutdownNow();
            try {
                authenticatedChannel.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (unauthenticatedChannel != null) {
            unauthenticatedChannel.shutdownNow();
            try {
                unauthenticatedChannel.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    @Override
    @Disabled
    public void testGetAgentCardIsPublic() {
        // gRPC doesn't have a separate /.well-known/agent-card.json endpoint
    }

    @Test
    @Override
    @Disabled
    public void testBasicAuthWorksViaHttp() {
        // HTTP-specific test — not applicable for gRPC transport
    }
}
