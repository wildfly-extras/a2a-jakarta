package org.wildfly.a2a.jakarta.test.grpc.compat03.auth;

import org.wildfly.a2a.jakarta.test.grpc.compat03.RestApplication;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.api.AnnotationsProto;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.protobuf.util.JsonFormat;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import mutiny.zero.ZeroPublisher;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.compat03.client.ClientBuilder_v0_3;
import org.a2aproject.sdk.compat03.client.transport.grpc.GrpcTransport_v0_3;
import org.a2aproject.sdk.compat03.client.transport.grpc.GrpcTransportConfigBuilder_v0_3;
import org.a2aproject.sdk.compat03.client.transport.grpc.GrpcTransportProvider_v0_3;
import org.a2aproject.sdk.compat03.client.transport.spi.interceptors.auth.AuthInterceptor_v0_3;
import org.a2aproject.sdk.compat03.conversion.AbstractA2AServerServerTest_v0_3;
import org.a2aproject.sdk.compat03.conversion.AbstractA2AServerWithAuthTest_v0_3;
import org.a2aproject.sdk.compat03.conversion.Convert_v0_3_To10RequestHandler;
import org.a2aproject.sdk.compat03.grpc.A2AServiceGrpc;
import org.a2aproject.sdk.compat03.spec.AgentCard_v0_3;
import org.a2aproject.sdk.compat03.spec.TransportProtocol_v0_3;
import org.a2aproject.sdk.compat03.transport.grpc.handler.GrpcHandler_v0_3;
import org.a2aproject.sdk.compat03.transport.jsonrpc.handler.JSONRPCHandler_v0_3;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.integrations.microprofile.MicroProfileConfigProvider;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.apps.common.AbstractA2AServerTest;
import org.a2aproject.sdk.spec.Event;
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
import org.wildfly.a2a.jakarta.grpc.compat03.WildFlyGrpcHandler_v0_3;
import org.wildfly.a2a.jakarta.test.common.BasicAuthGrpcInterceptor;
import org.wildfly.a2a.jakarta.jsonrpc.compat03.A2AServerResourceDelegate_v0_3;
import org.wildfly.a2a.jakarta.jsonrpc.compat03.A2AServerResource_v0_3;

@ArquillianTest
@RunAsClient
public class WildFlyA2A_v0_3_GrpcWithAuthTestCase extends AbstractA2AServerWithAuthTest_v0_3 {

    private static ManagedChannel authenticatedChannel;
    private static ManagedChannel unauthenticatedChannel;

    public WildFlyA2A_v0_3_GrpcWithAuthTestCase() {
        super(8080);
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol_v0_3.GRPC.asString();
    }

    @Override
    protected String getTransportUrl() {
        return "localhost:9555";
    }

    @Override
    protected void configureTransport(ClientBuilder_v0_3 builder) {
        builder.withTransport(GrpcTransport_v0_3.class, new GrpcTransportConfigBuilder_v0_3().channelFactory(target -> {
            unauthenticatedChannel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
            return unauthenticatedChannel;
        }));
    }

    @Override
    protected void configureTransportWithAuth(ClientBuilder_v0_3 builder) {
        AuthInterceptor_v0_3 authInterceptor = new AuthInterceptor_v0_3(
                (schemeName, context) ->
                        BASIC_AUTH_SCHEME_NAME.equals(schemeName) ? getEncodedCredentials() : null);
        builder.withTransport(GrpcTransport_v0_3.class, new GrpcTransportConfigBuilder_v0_3()
                .channelFactory(target -> {
                    authenticatedChannel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
                    return authenticatedChannel;
                })
                .addInterceptor(authInterceptor));
    }

    @Deployment
    public static WebArchive createDeployment() throws Exception {
        JavaArchive v03TestJar = getJarForClass(AbstractA2AServerServerTest_v0_3.class);

        JavaArchive specGrpcJar = getJarForClass(JSONRPCUtils.class);
        specGrpcJar.delete("/org/a2aproject/sdk/grpc/A2AServiceGrpc$A2AServiceImplBase.class");

        final JavaArchive[] libraries = List.of(
                getJarForClass(WildFlyGrpcHandler_v0_3.class),
                getJarForClass(A2AServerResourceDelegate_v0_3.class),
                getJarForClass(A2AServerResource_v0_3.class),
                getJarForClass(JSONRPCHandler_v0_3.class),
                getJarForClass(A2A.class),
                getJarForClass(Assert.class),
                getJarForClass(A2AHttpClient.class),
                getJarForClass(PublicAgentCard.class),
                getJarForClass(Event.class),
                specGrpcJar,
                getJarForClass(JsonUtil.class),
                getJarForClass(Gson.class),
                getJarForClass(com.google.protobuf.Message.class),
                getJarForClass(JsonFormat.class),
                getJarForClass(AnnotationsProto.class),
                getJarForClass(ImmutableSet.class),
                getJarForClass(MicroProfileConfigProvider.class),
                getJarForClass(GrpcHandler_v0_3.class),
                getJarForClass(A2AServiceGrpc.class),
                getJarForClass(Convert_v0_3_To10RequestHandler.class),
                getJarForClass(AgentCard_v0_3.class),
                getJarForClass(ZeroPublisher.class),
                getJarForClass(ClientTransport.class),
                getJarForClass(GrpcTransportProvider_v0_3.class),
                v03TestJar,
                getJarForClass(AsyncManagedExecutorServiceProducer.class)).toArray(JavaArchive[]::new);

        String manifest = "Manifest-Version: 1.0\n" +
                "Dependencies: io.grpc-all\n";

        return ShrinkWrap.create(WebArchive.class, "ROOT.war")
                .addAsLibraries(libraries)
                .addPackage(AbstractA2AServerTest.class.getPackage())
                .addPackage(RestApplication.class.getPackage())
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
