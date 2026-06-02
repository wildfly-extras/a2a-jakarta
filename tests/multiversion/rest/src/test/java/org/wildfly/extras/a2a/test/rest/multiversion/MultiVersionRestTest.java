package org.wildfly.extras.a2a.test.rest.multiversion;

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
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.compat03.conversion.AbstractA2AServerServerTest_v0_3;
import org.a2aproject.sdk.compat03.conversion.Convert_v0_3_To10RequestHandler;
import org.a2aproject.sdk.compat03.grpc.A2AServiceGrpc;
import org.a2aproject.sdk.compat03.spec.AgentCard_v0_3;
import org.a2aproject.sdk.compat03.transport.rest.handler.RestHandler_v0_3;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.integrations.microprofile.MicroProfileConfigProvider;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.apps.common.AbstractA2AServerTest;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.a2aproject.sdk.util.Assert;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.wildfly.extras.a2a.common.SSESubscriber;
import org.wildfly.extras.a2a.rest.A2ARestServerResourceDelegate;
import org.wildfly.extras.a2a.rest.compat03.A2ARestServerResourceDelegate_v0_3;
import org.wildfly.extras.a2a.rest.multiversion.MultiVersionA2ARestServerResource;
import org.wildfly.extras.a2a.test.rest.multiversion.producer.MultiVersionAgentCardProducer;


@ArquillianTest
@RunAsClient
public class MultiVersionRestTest extends AbstractA2AServerTest {

    public MultiVersionRestTest() {
        super(8080);
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol.HTTP_JSON.asString();
    }

    @Override
    protected String getTransportUrl() {
        return "http://localhost:8080/v1";
    }

    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(RestTransport.class, new RestTransportConfigBuilder());
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
                // a2a-java-sdk-transport-rest (v1.0)
                getJarForClass(RestHandler.class),
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
                // a2a-jakarta-common.jar (contains SSESubscriber)
                getJarForClass(SSESubscriber.class),
                // a2a-jakarta-compat-0.3-multiversion-rest.jar - contains MultiVersionA2ARestServerResource
                getJarForClass(MultiVersionA2ARestServerResource.class),
                // a2a-jakarta-rest.jar - contains v1.0 delegate
                getJarForClass(A2ARestServerResourceDelegate.class),
                // a2a-jakarta-compat-0.3-rest.jar - contains v0.3 delegate
                getJarForClass(A2ARestServerResourceDelegate_v0_3.class),
                // v0.3 transport-rest
                getJarForClass(RestHandler_v0_3.class),
                // v0.3 server-conversion
                getJarForClass(Convert_v0_3_To10RequestHandler.class),
                // v0.3 spec types
                getJarForClass(AgentCard_v0_3.class),
                // v0.3 spec-grpc (required transitively by v0.3 transport-rest)
                getJarForClass(A2AServiceGrpc.class),
                // a2a-java-sdk-microprofile-config.jar (needed to configure a2a-java settings via MP Config)
                getJarForClass(MicroProfileConfigProvider.class),
                // mutiny-zero.jar. This is provided by some WildFly layers, but not always, and not in
                // the server provisioned by Glow when inspecting our war
                getJarForClass(ZeroPublisher.class),
                // a2a-java-sdk-client.jar (client library)
                getJarForClass(ClientConfig.class),
                // a2a-java-sdk-client-transport-spi.jar (client transport SPI)
                getJarForClass(ClientTransport.class),
                // a2a-java-sdk-client-transport-rest.jar (v1.0 REST client transport)
                getJarForClass(RestTransport.class),
                // a2a-java-sdk-compat-0.3-server-conversion test-jar (v0.3 CDI producers for testing)
                v03TestJar).toArray(JavaArchive[]::new);


        WebArchive archive = ShrinkWrap.create(WebArchive.class, "ROOT.war")
                .addAsLibraries(libraries)
                // Extra dependencies needed by the tests
                .addPackage(AbstractA2AServerTest.class.getPackage())
                .addPackage(A2ATestResource.class.getPackage())
                .addClass(MultiVersionAgentCardProducer.class);
        // Remove upstream AgentCardProducer so our MultiVersionAgentCardProducer
        // (which adds /v1 to the transport URL) is used instead
        archive.delete("/WEB-INF/classes/org/a2aproject/sdk/server/apps/common/AgentCardProducer.class");
        archive
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
