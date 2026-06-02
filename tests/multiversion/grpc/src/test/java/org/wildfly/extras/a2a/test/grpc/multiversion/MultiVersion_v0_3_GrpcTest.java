package org.wildfly.extras.a2a.test.grpc.multiversion;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.api.AnnotationsProto;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.protobuf.util.JsonFormat;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.compat03.client.ClientBuilder_v0_3;
import org.a2aproject.sdk.compat03.client.transport.grpc.GrpcTransport_v0_3;
import org.a2aproject.sdk.compat03.client.transport.grpc.GrpcTransportConfigBuilder_v0_3;
import org.a2aproject.sdk.compat03.client.transport.grpc.GrpcTransportProvider_v0_3;
import org.a2aproject.sdk.compat03.conversion.AbstractA2AServerServerTest_v0_3;
import org.a2aproject.sdk.compat03.conversion.Convert_v0_3_To10RequestHandler;
import org.a2aproject.sdk.compat03.spec.AgentCard_v0_3;
import org.a2aproject.sdk.compat03.spec.TransportProtocol_v0_3;
import org.a2aproject.sdk.compat03.transport.grpc.handler.GrpcHandler_v0_3;
import org.a2aproject.sdk.compat03.transport.jsonrpc.handler.JSONRPCHandler_v0_3;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.a2aproject.sdk.grpc.A2AServiceGrpc;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.integrations.microprofile.MicroProfileConfigProvider;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.apps.common.AbstractA2AServerTest;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.transport.grpc.handler.GrpcHandler;
import org.a2aproject.sdk.util.Assert;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import mutiny.zero.ZeroPublisher;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.AfterAll;
import org.wildfly.extras.a2a.common.AsyncManagedExecutorServiceProducer;
import org.wildfly.extras.a2a.grpc.WildFlyGrpcHandler;
import org.wildfly.extras.a2a.grpc.compat03.WildFlyGrpcHandler_v0_3;
import org.wildfly.extras.a2a.jsonrpc.A2AServerResourceDelegate;
import org.wildfly.extras.a2a.jsonrpc.compat03.A2AServerResourceDelegate_v0_3;
import org.wildfly.extras.a2a.jsonrpc.multiversion.MultiVersionA2AServerResource;

@ArquillianTest
@RunAsClient
public class MultiVersion_v0_3_GrpcTest extends AbstractA2AServerServerTest_v0_3 {

    private static ManagedChannel channel;

    public MultiVersion_v0_3_GrpcTest() {
        super(8080); // HTTP server port for utility endpoints
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol_v0_3.GRPC.asString();
    }

    @Override
    protected String getTransportUrl() {
        // gRPC port (from WildFly gRPC configuration)
        return "localhost:9555";
    }

    @Override
    protected void configureTransport(ClientBuilder_v0_3 builder) {
        builder.withTransport(GrpcTransport_v0_3.class, new GrpcTransportConfigBuilder_v0_3().channelFactory(target -> {
            channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
            return channel;
        }));
    }

    @Deployment
    public static WebArchive createDeployment() throws Exception {
        JavaArchive v03TestJar = getJarForClass(AbstractA2AServerServerTest_v0_3.class);

        final JavaArchive[] libraries = List.of(
                // a2a-jakarta-grpc.jar (v1.0)
                getJarForClass(WildFlyGrpcHandler.class),
                // a2a-jakarta-compat-0.3-grpc.jar (v0.3)
                getJarForClass(WildFlyGrpcHandler_v0_3.class),
                // a2a-jakarta-compat-0.3-multiversion-jsonrpc.jar - needed for agent card endpoint
                getJarForClass(MultiVersionA2AServerResource.class),
                // a2a-jakarta-jsonrpc.jar - contains v1.0 delegate
                getJarForClass(A2AServerResourceDelegate.class),
                // a2a-jakarta-compat-0.3-jsonrpc.jar - contains v0.3 delegate
                getJarForClass(A2AServerResourceDelegate_v0_3.class),
                // v1.0 transport-jsonrpc (needed by MultiVersionA2AServerResource)
                getJarForClass(JSONRPCHandler.class),
                // v0.3 transport-jsonrpc (needed by MultiVersionA2AServerResource)
                getJarForClass(JSONRPCHandler_v0_3.class),
                // a2a-java-sdk-client.jar
                getJarForClass(A2A.class),
                // a2a-java-sdk-common.jar
                getJarForClass(Assert.class),
                // a2a-java-sdk-http-client
                getJarForClass(A2AHttpClient.class),
                // a2a-java-sdk-server-common.jar
                getJarForClass(PublicAgentCard.class),
                // a2a-java-sdk-spec.jar
                getJarForClass(Event.class),
                // a2a-java-sdk-spec-grpc.jar (v1.0, contains JSONRPCUtils)
                getJarForClass(JSONRPCUtils.class),
                // a2a-java-transport-grpc.jar (v1.0)
                getJarForClass(GrpcHandler.class),
                // a2a-java-sdk-jsonrpc-common.jar
                getJarForClass(JsonUtil.class),
                // gson.jar (required by jsonrpc-common)
                getJarForClass(Gson.class),
                // protobuf-java.jar - include correct version to match gencode
                getJarForClass(com.google.protobuf.Message.class),
                // protobuf-java-util.jar (required by spec-grpc JSONRPCUtils)
                getJarForClass(JsonFormat.class),
                // proto-google-common-protos.jar (required by spec-grpc)
                getJarForClass(AnnotationsProto.class),
                // guava.jar (required by a2a-java dependencies)
                getJarForClass(ImmutableSet.class),
                // a2a-java-sdk-microprofile-config.jar (needed to configure a2a-java settings via MP Config)
                getJarForClass(MicroProfileConfigProvider.class),
                // v1.0 spec-grpc (contains generated gRPC classes)
                getJarForClass(A2AServiceGrpc.class),
                // v0.3 transport-grpc
                getJarForClass(GrpcHandler_v0_3.class),
                // v0.3 spec-grpc (contains v0.3 generated gRPC classes)
                getJarForClass(org.a2aproject.sdk.compat03.grpc.A2AServiceGrpc.class),
                // v0.3 server-conversion
                getJarForClass(Convert_v0_3_To10RequestHandler.class),
                // v0.3 spec types
                getJarForClass(AgentCard_v0_3.class),
                // mutiny-zero.jar. This is provided by some WildFly layers, but not always, and not in
                // the server provisioned by Glow when inspecting our war
                getJarForClass(ZeroPublisher.class),
                // a2a-java-sdk-client-transport-spi.jar (client transport SPI)
                getJarForClass(ClientTransport.class),
                // a2a-java-sdk-compat-0.3-client-transport-grpc.jar (v0.3 gRPC client transport)
                getJarForClass(GrpcTransportProvider_v0_3.class),
                // a2a-java-sdk-compat-0.3-server-conversion test-jar (v0.3 CDI producers for testing)
                v03TestJar,
                // a2a-jakarta-common.jar (ManagedExecutor for RequestScoped bean injection into AgentExecutors)
                getJarForClass(AsyncManagedExecutorServiceProducer.class)).toArray(JavaArchive[]::new);

        // Create MANIFEST.MF with gRPC module dependencies
        // These are provided by WildFly's gRPC feature pack and should not be packaged in WAR
        // meta-inf export makes the module classes visible to all classloaders in the deployment
        String manifest = "Manifest-Version: 1.0\n" +
                "Dependencies: io.grpc-all\n";

        return ShrinkWrap.create(WebArchive.class, "ROOT.war")
                .addAsLibraries(libraries)
                // Extra dependencies needed by the tests
                .addPackage(AbstractA2AServerTest.class.getPackage())
                .addPackage(RestApplication.class.getPackage())
                .addAsWebInfResource("WEB-INF/web.xml")
                .addAsWebInfResource("META-INF/beans.xml", "beans.xml")
                // Add test properties file for AgentCardProducer
                .addAsResource("a2a-requesthandler-test.properties")
                // Add MANIFEST.MF with gRPC module dependencies from WildFly feature pack
                .setManifest(new StringAsset(manifest));
    }

    static JavaArchive getJarForClass(Class<?> clazz) throws Exception {
        File f = new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
        return ShrinkWrap.createFromZipFile(JavaArchive.class, f);
    }

    @AfterAll
    public static void closeChannel() {
        channel.shutdownNow();
        try {
            channel.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
