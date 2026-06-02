package org.wildfly.extras.a2a.test.jsonrpc.multiversion;

import java.io.File;
import java.util.List;

import com.google.api.AnnotationsProto;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import mutiny.zero.ZeroPublisher;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportProvider;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.compat03.conversion.AbstractA2AServerServerTest_v0_3;
import org.a2aproject.sdk.compat03.conversion.Convert_v0_3_To10RequestHandler;
import org.a2aproject.sdk.compat03.spec.AgentCard_v0_3;
import org.a2aproject.sdk.compat03.transport.jsonrpc.handler.JSONRPCHandler_v0_3;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.integrations.microprofile.MicroProfileConfigProvider;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.apps.common.AbstractA2AServerTest;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.a2aproject.sdk.util.Assert;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.wildfly.extras.a2a.common.AsyncManagedExecutorServiceProducer;
import org.wildfly.extras.a2a.jsonrpc.A2AServerResourceDelegate;
import org.wildfly.extras.a2a.jsonrpc.compat03.A2AServerResourceDelegate_v0_3;
import org.wildfly.extras.a2a.jsonrpc.multiversion.MultiVersionA2AServerResource;


@ArquillianTest
@RunAsClient
public class MultiVersionJSONRPCTest extends AbstractA2AServerTest {

    public MultiVersionJSONRPCTest() {
        super(8080);
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol.JSONRPC.asString();
    }

    @Override
    protected String getTransportUrl() {
        return "http://localhost:8080";
    }

    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder());
    }

    @Deployment
    public static WebArchive createTestArchive() throws Exception {
        JavaArchive v03TestJar = getJarForClass(AbstractA2AServerServerTest_v0_3.class);

        final JavaArchive[] libraries = List.of(
                // a2a-java-sdk-common.jar
                getJarForClass(Assert.class),
                // a2a-java-sdk-http-client
                getJarForClass(A2AHttpClient.class),
                // a2a-java-sdk-server-common.jar
                getJarForClass(PublicAgentCard.class),
                // a2a-java-sdk-spec.jar
                getJarForClass(Event.class),
                // a2a-java-sdk-spec-grpc.jar (contains JSONRPCUtils)
                getJarForClass(JSONRPCUtils.class),
                // a2a-java-sdk-transport-jsonrpc (v1.0)
                getJarForClass(JSONRPCHandler.class),
                // a2a-java-sdk-jsonrpc-common.jar
                getJarForClass(JsonUtil.class),
                // gson.jar (required by jsonrpc-common)
                getJarForClass(Gson.class),
                // protobuf-java.jar (required by spec-grpc)
                getJarForClass(InvalidProtocolBufferException.class),
                // protobuf-java-util.jar (required by spec-grpc JSONRPCUtils)
                getJarForClass(JsonFormat.class),
                // proto-google-common-protos.jar (required by spec-grpc)
                getJarForClass(AnnotationsProto.class),
                // guava.jar (required by a2a-java dependencies)
                getJarForClass(ImmutableSet.class),
                // a2a-jakarta-compat-0.3-multiversion-jsonrpc.jar - contains MultiVersionA2AServerResource
                getJarForClass(MultiVersionA2AServerResource.class),
                // a2a-jakarta-jsonrpc.jar - contains v1.0 delegate
                getJarForClass(A2AServerResourceDelegate.class),
                // a2a-jakarta-compat-0.3-jsonrpc.jar - contains v0.3 delegate
                getJarForClass(A2AServerResourceDelegate_v0_3.class),
                // v0.3 transport-jsonrpc
                getJarForClass(JSONRPCHandler_v0_3.class),
                // v0.3 server-conversion
                getJarForClass(Convert_v0_3_To10RequestHandler.class),
                // v0.3 spec types
                getJarForClass(AgentCard_v0_3.class),
                // a2a-java-sdk-microprofile-config.jar (needed to configure a2a-java settings via MP Config)
                getJarForClass(MicroProfileConfigProvider.class),
                // mutiny-zero.jar. This is provided by some WildFly layers, but not always, and not in
                // the server provisioned by Glow when inspecting our war
                getJarForClass(ZeroPublisher.class),
                // a2a-java-sdk-client.jar (client library)
                getJarForClass(ClientConfig.class),
                // a2a-java-sdk-client-transport-spi.jar (client transport SPI)
                getJarForClass(ClientTransport.class),
                // a2a-java-sdk-client-transport-jsonrpc.jar (v1.0 JSONRPC client transport)
                getJarForClass(JSONRPCTransportProvider.class),
                // a2a-java-sdk-compat-0.3-server-conversion test-jar (v0.3 CDI producers for testing)
                v03TestJar,
                // a2a-jakarta-common.jar (ManagedExecutor for RequestScoped bean injection into AgentExecutors)
                getJarForClass(AsyncManagedExecutorServiceProducer.class)).toArray(JavaArchive[]::new);


        WebArchive archive = ShrinkWrap.create(WebArchive.class, "ROOT.war")
                .addAsLibraries(libraries)
                // Extra dependencies needed by the tests
                .addPackage(AbstractA2AServerTest.class.getPackage())
                .addPackage(A2ATestResource.class.getPackage())
                // Add deployment descriptors
                .addAsManifestResource("META-INF/beans.xml", "beans.xml")
                .addAsWebInfResource("WEB-INF/web.xml", "web.xml")
                // Add test properties file for AgentCardProducer
                .addAsResource("a2a-requesthandler-test.properties");
        archive.toString(true);
        return archive;
    }

    static JavaArchive getJarForClass(Class<?> clazz) throws Exception {
        File f = new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
        return ShrinkWrap.createFromZipFile(JavaArchive.class, f);
    }
}
