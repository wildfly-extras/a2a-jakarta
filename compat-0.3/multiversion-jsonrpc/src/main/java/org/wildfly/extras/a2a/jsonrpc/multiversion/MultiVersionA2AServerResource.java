package org.wildfly.extras.a2a.jsonrpc.multiversion;

import java.io.IOException;
import java.util.concurrent.Executor;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import org.a2aproject.sdk.common.A2AHeaders;
import org.a2aproject.sdk.compat03.transport.jsonrpc.handler.JSONRPCHandler_v0_3;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.server.util.async.Internal;
import org.a2aproject.sdk.spec.VersionNotSupportedError;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.wildfly.extras.a2a.jsonrpc.A2AServerResourceDelegate;
import org.wildfly.extras.a2a.jsonrpc.compat03.A2AServerResourceDelegate_v0_3;

@Path("/")
public class MultiVersionA2AServerResource {

    private static final String VERSION_1_0 = "1.0";
    private static final String VERSION_0_3 = "0.3";

    @Inject
    JSONRPCHandler jsonRpcHandler;

    @Inject
    JSONRPCHandler_v0_3 jsonRpcHandler_v0_3;

    @Inject
    @Internal
    Executor executor;

    private A2AServerResourceDelegate v10Delegate;
    private A2AServerResourceDelegate_v0_3 v03Delegate;

    private A2AServerResourceDelegate getV10Delegate() {
        if (v10Delegate == null) {
            v10Delegate = new A2AServerResourceDelegate(jsonRpcHandler);
        }
        return v10Delegate;
    }

    private A2AServerResourceDelegate_v0_3 getV03Delegate() {
        if (v03Delegate == null) {
            v03Delegate = new A2AServerResourceDelegate_v0_3(jsonRpcHandler_v0_3);
        }
        return v03Delegate;
    }

    @GET
    @Path("/.well-known/agent-card.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAgentCard() {
        return getV10Delegate().getAgentCard();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleNonStreamingRequests(
            String body,
            @Context HttpServletRequest httpRequest,
            @Context SecurityContext securityContext) {

        String version = resolveVersion(httpRequest);

        if (isV10(version)) {
            return getV10Delegate().handleNonStreamingRequests(body, httpRequest, securityContext);
        } else if (isV03(version)) {
            return getV03Delegate().handleNonStreamingRequests(body, httpRequest, securityContext);
        } else {
            return createVersionNotSupportedResponse(version);
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void handleStreamingRequests(
            String body,
            @Context HttpServletResponse response,
            @Context HttpServletRequest httpRequest,
            @Context SecurityContext securityContext) throws IOException {

        String version = resolveVersion(httpRequest);

        if (isV10(version)) {
            getV10Delegate().handleStreamingRequests(body, response, httpRequest, securityContext);
        } else if (isV03(version)) {
            getV03Delegate().handleStreamingRequests(body, response, httpRequest, securityContext);
        } else {
            VersionNotSupportedError error = new VersionNotSupportedError(
                    null,
                    "Protocol version '" + version + "' is not supported. Supported versions: [1.0, 0.3]",
                    null);
            String serialized = JSONRPCUtils.toJsonRPCErrorResponse(null, error);
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(org.a2aproject.sdk.common.MediaType.APPLICATION_JSON);
            response.getWriter().write(serialized);
            response.getWriter().flush();
        }
    }

    private Response createVersionNotSupportedResponse(String version) {
        VersionNotSupportedError error = new VersionNotSupportedError(
                null,
                "Protocol version '" + version + "' is not supported. Supported versions: [1.0, 0.3]",
                null);
        String serialized = JSONRPCUtils.toJsonRPCErrorResponse(null, error);
        return Response.status(Response.Status.OK)
                .header(HttpHeaders.CONTENT_TYPE, org.a2aproject.sdk.common.MediaType.APPLICATION_JSON)
                .entity(serialized)
                .build();
    }

    private static String resolveVersion(HttpServletRequest request) {
        String version = request.getHeader(A2AHeaders.A2A_VERSION);
        if (version == null || version.isBlank()) {
            return VERSION_0_3;
        }
        return version.trim();
    }

    private static boolean isV10(String resolvedVersion) {
        return VERSION_1_0.equals(resolvedVersion);
    }

    private static boolean isV03(String resolvedVersion) {
        return VERSION_0_3.equals(resolvedVersion);
    }

    public static void setStreamingIsSubscribedRunnable(Runnable streamingIsSubscribedRunnable) {
        A2AServerResourceDelegate.setStreamingIsSubscribedRunnable(streamingIsSubscribedRunnable);
        A2AServerResourceDelegate_v0_3.setStreamingIsSubscribedRunnable(streamingIsSubscribedRunnable);
    }
}
