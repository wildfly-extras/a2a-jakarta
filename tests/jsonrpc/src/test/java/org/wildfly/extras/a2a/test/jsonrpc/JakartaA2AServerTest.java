package org.wildfly.extras.a2a.test.jsonrpc;


import java.io.File;
import java.util.List;

import com.google.api.AnnotationsProto;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportProvider;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.common.MediaType;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.integrations.microprofile.MicroProfileConfigProvider;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2AErrorResponse;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.apps.common.AbstractA2AServerTest;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.a2aproject.sdk.util.Assert;
import io.restassured.response.Response;
import mutiny.zero.ZeroPublisher;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.wildfly.extras.a2a.common.AsyncManagedExecutorServiceProducer;
import org.wildfly.extras.a2a.jsonrpc.web.A2AServerResource;
import org.wildfly.extras.a2a.jsonrpc.WildFlyJSONRPCTransportMetadata;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


@ArquillianTest
@RunAsClient
public class JakartaA2AServerTest extends AbstractA2AServerTest {

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

    public JakartaA2AServerTest() {
        super(8080);
    }

    @Deployment
    public static WebArchive createTestArchive() throws Exception {
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
                // a2a-java-sdk-transport-jsonrpc
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
                // a2a-jakarta-jsonrpc.jar - contains delegate and WildFlyJSONRPCTransportMetadata
                getJarForClass(WildFlyJSONRPCTransportMetadata.class),
                // a2a-jakarta-jsonrpc-web.jar - contains A2AServerResource
                getJarForClass(A2AServerResource.class),
                //a2a-java-sdk-microprofile-config.jar (needed to configure a2a-java settings via MP Config)
                getJarForClass(MicroProfileConfigProvider.class),
                // mutiny-zero.jar. This is provided by some WildFly layers, but not always, and not in
                // the server provisioned by Glow when inspecting our war
                getJarForClass(ZeroPublisher.class),
                // a2a-java-sdk-client.jar (client library)
                getJarForClass(ClientConfig.class),
                // a2a-java-sdk-client-transport-spi.jar (client transport SPI)
                getJarForClass(ClientTransport.class),
                // a2a-java-sdk-client-transport-jsonrpc.jar (JSONRPC client transport)
                getJarForClass(JSONRPCTransportProvider.class),
                // a2a-jakarta-common.jar (ManagedExecutor for RequestScoped bean injection into AgentExecutors)
                getJarForClass(AsyncManagedExecutorServiceProducer.class)).toArray(new JavaArchive[0]);


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

    @Test
    public void testErrorResponseContentType() {
        // Test that error responses use application/problem+json content-type as per A2A spec
        String getTaskRequest = """
            {"jsonrpc": "2.0", "method": "GetTask", "params": {"taskId": "non-existent-task-id"}, "id": "1"}
            """;

        Response response = given()
                .contentType(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
                .body(getTaskRequest)
                .when()
                .post("/")
                .then()
                .statusCode(200)
                .extract()
                .response();

        // Validate content-type header
        String contentType = response.getContentType();
        assertNotNull(contentType, "Content-Type header should be present");
        assertEquals(MediaType.APPLICATION_JSON, contentType,
                "Error responses must use application/problem+json content-type");

        // Validate it's actually an error response
        A2AErrorResponse errorResponse = response.as(A2AErrorResponse.class);
        assertNotNull(errorResponse.getError(), "Response should contain an error");
    }

}
