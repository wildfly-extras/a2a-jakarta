package org.wildfly.a2a.jakarta.test.jsonrpc.compat03;

import static org.wildfly.a2a.jakarta.test.common.ArchiveUtils.getJarForClass;
import static org.wildfly.a2a.jakarta.test.common.producer.SecurityAwareMultiVersionAgentCardProducer.DEFAULT_TEST_PORT;

import java.util.List;

import com.google.api.AnnotationsProto;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import mutiny.zero.ZeroPublisher;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.compat03.client.ClientBuilder_v0_3;
import org.a2aproject.sdk.compat03.client.transport.jsonrpc.JSONRPCTransport_v0_3;
import org.a2aproject.sdk.compat03.client.transport.jsonrpc.JSONRPCTransportConfigBuilder_v0_3;
import org.a2aproject.sdk.compat03.client.transport.spi.interceptors.auth.AuthInterceptor_v0_3;
import org.a2aproject.sdk.compat03.conversion.AbstractA2AServerServerTest_v0_3;
import org.a2aproject.sdk.compat03.conversion.AbstractA2AServerWithTaskAuthorizationTest_v0_3;
import org.a2aproject.sdk.compat03.conversion.Convert_v0_3_To10RequestHandler;
import org.a2aproject.sdk.compat03.spec.AgentCard_v0_3;
import org.a2aproject.sdk.compat03.spec.TransportProtocol_v0_3;
import org.a2aproject.sdk.compat03.transport.jsonrpc.handler.JSONRPCHandler_v0_3;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.integrations.microprofile.MicroProfileConfigProvider;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.apps.common.AbstractA2AServerTest;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.a2aproject.sdk.util.Assert;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.wildfly.a2a.jakarta.common.AsyncManagedExecutorServiceProducer;
import org.wildfly.a2a.jakarta.jsonrpc.compat03.A2AServerResource_v0_3;
import org.wildfly.a2a.jakarta.test.common.ElytronSetupTask;

@ArquillianTest
@RunAsClient
@ServerSetup(ElytronSetupTask.class)
public class JakartaA2AServer_v0_3_WithTaskAuthorizationJSONRPCTest extends AbstractA2AServerWithTaskAuthorizationTest_v0_3 {

    private static final int PORT = Integer.parseInt(
            System.getProperty("test.agent.card.port", DEFAULT_TEST_PORT));

    public JakartaA2AServer_v0_3_WithTaskAuthorizationJSONRPCTest() {
        super(PORT);
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol_v0_3.JSONRPC.asString();
    }

    @Override
    protected String getTransportUrl() {
        return "http://localhost:" + PORT;
    }

    @Override
    protected void configureTransportWithCredentials(ClientBuilder_v0_3 builder, String username, String password) {
        AuthInterceptor_v0_3 authInterceptor = new AuthInterceptor_v0_3(
                (schemeName, context) -> BASIC_AUTH_SCHEME_NAME.equals(schemeName)
                        ? getEncodedCredentials(username, password) : null);
        builder.withTransport(JSONRPCTransport_v0_3.class,
                new JSONRPCTransportConfigBuilder_v0_3()
                        .addInterceptor(authInterceptor));
    }

    @Deployment
    public static WebArchive createTestArchive() throws Exception {
        JavaArchive v03TestJar = getJarForClass(AbstractA2AServerServerTest_v0_3.class);

        final JavaArchive[] libraries = List.of(
                getJarForClass(Assert.class),
                getJarForClass(A2AHttpClient.class),
                getJarForClass(PublicAgentCard.class),
                getJarForClass(Event.class),
                getJarForClass(JSONRPCUtils.class),
                getJarForClass(JSONRPCHandler.class),
                getJarForClass(JsonUtil.class),
                getJarForClass(Gson.class),
                getJarForClass(InvalidProtocolBufferException.class),
                getJarForClass(JsonFormat.class),
                getJarForClass(AnnotationsProto.class),
                getJarForClass(ImmutableSet.class),
                getJarForClass(A2AServerResource_v0_3.class),
                getJarForClass(JSONRPCHandler_v0_3.class),
                getJarForClass(Convert_v0_3_To10RequestHandler.class),
                getJarForClass(AgentCard_v0_3.class),
                getJarForClass(MicroProfileConfigProvider.class),
                getJarForClass(ZeroPublisher.class),
                getJarForClass(ClientConfig.class),
                getJarForClass(ClientTransport.class),
                getJarForClass(JSONRPCTransport_v0_3.class),
                v03TestJar,
                getJarForClass(AsyncManagedExecutorServiceProducer.class)).toArray(JavaArchive[]::new);

        WebArchive archive = ShrinkWrap.create(WebArchive.class, "ROOT.war")
                .addAsLibraries(libraries)
                .addPackage(AbstractA2AServerTest.class.getPackage())
                .addPackage(RestApplication.class.getPackage())
                .addAsManifestResource("META-INF/beans.xml", "beans.xml")
                .addAsWebInfResource("WEB-INF/web-auth.xml", "web.xml")
                .addAsWebInfResource("WEB-INF/jboss-web-auth.xml", "jboss-web.xml")
                .addAsResource("a2a-requesthandler-test.properties")
                .addAsResource("META-INF/auth-microprofile-config.properties",
                        "META-INF/microprofile-config.properties");
        return archive;
    }

}
