package org.wildfly.a2a.jakarta.test.rest;

import static org.wildfly.a2a.jakarta.test.common.ArchiveUtils.getJarForClass;

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
import org.a2aproject.sdk.client.transport.spi.interceptors.auth.AuthInterceptor;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.integrations.microprofile.MicroProfileConfigProvider;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.apps.common.AbstractA2AServerTest;
import org.a2aproject.sdk.server.apps.common.AbstractA2AServerWithAuthTest;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.a2aproject.sdk.util.Assert;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.wildfly.a2a.jakarta.common.SSESubscriber;
import org.wildfly.a2a.jakarta.rest.A2ARestServerResourceDelegate;
import org.wildfly.a2a.jakarta.rest.A2ARestServerResource;
import org.wildfly.a2a.jakarta.test.common.ElytronSetupTask;


@ArquillianTest
@RunAsClient
@ServerSetup(ElytronSetupTask.class)
public class JakartaA2AServerWithAuthTest extends AbstractA2AServerWithAuthTest {

    public JakartaA2AServerWithAuthTest() {
        super(8080);
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol.HTTP_JSON.asString();
    }

    @Override
    protected String getTransportUrl() {
        return "http://localhost:8080";
    }

    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(RestTransport.class, new RestTransportConfigBuilder());
    }

    @Override
    protected void configureTransportWithAuth(ClientBuilder builder) {
        AuthInterceptor authInterceptor = new AuthInterceptor(
                (schemeName, context) ->
                        BASIC_AUTH_SCHEME_NAME.equals(schemeName) ? getEncodedCredentials() : null);
        builder.withTransport(RestTransport.class,
                new RestTransportConfigBuilder()
                        .addInterceptor(authInterceptor));
    }

    @Deployment
    public static WebArchive createTestArchive() throws Exception {
        final JavaArchive[] libraries = List.of(
                getJarForClass(Assert.class),
                getJarForClass(A2AHttpClient.class),
                getJarForClass(PublicAgentCard.class),
                getJarForClass(Event.class),
                getJarForClass(JSONRPCUtils.class),
                getJarForClass(RestHandler.class),
                getJarForClass(JsonUtil.class),
                getJarForClass(Gson.class),
                getJarForClass(InvalidProtocolBufferException.class),
                getJarForClass(JsonFormat.class),
                getJarForClass(AnnotationsProto.class),
                getJarForClass(ImmutableSet.class),
                getJarForClass(SSESubscriber.class),
                getJarForClass(A2ARestServerResourceDelegate.class),
                getJarForClass(A2ARestServerResource.class),
                getJarForClass(MicroProfileConfigProvider.class),
                getJarForClass(ZeroPublisher.class),
                getJarForClass(ClientConfig.class),
                getJarForClass(ClientTransport.class),
                getJarForClass(RestTransport.class)).toArray(JavaArchive[]::new);

        WebArchive archive = ShrinkWrap.create(WebArchive.class, "ROOT.war")
                .addAsLibraries(libraries)
                .addPackage(AbstractA2AServerTest.class.getPackage())
                .addPackage(A2ATestResource.class.getPackage())
                .addAsManifestResource("META-INF/beans.xml", "beans.xml")
                .addAsWebInfResource("WEB-INF/web-auth.xml", "web.xml")
                .addAsWebInfResource("WEB-INF/jboss-web-auth.xml", "jboss-web.xml")
                .addAsResource("a2a-requesthandler-test.properties")
                .addAsResource("META-INF/auth-microprofile-config.properties",
                        "META-INF/microprofile-config.properties");
        archive.toString(true);
        return archive;
    }

    @Test
    @Override
    public void testBasicAuthWorksViaHttp() throws Exception {
        saveTaskInTaskStore(MINIMAL_TASK);
        givenAuthenticated()
                .get("/tasks/" + MINIMAL_TASK.id())
                .then()
                .statusCode(200);
        deleteTaskInTaskStore(MINIMAL_TASK.id());
    }
}
