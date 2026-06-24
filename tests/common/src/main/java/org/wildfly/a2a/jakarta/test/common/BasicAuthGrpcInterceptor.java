package org.wildfly.a2a.jakarta.test.common;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * Server-side gRPC interceptor that enforces HTTP Basic Auth on gRPC calls.
 * <p>
 * WildFly's gRPC subsystem auto-discovers {@link ServerInterceptor} implementations
 * in the deployment and applies them. This interceptor validates the {@code authorization}
 * metadata header against hardcoded test credentials (testuser/testpass).
 * <p>
 * Unlike HTTP transports where {@code web.xml} security constraints protect endpoints,
 * gRPC runs on a separate port (9555) outside of Undertow, so auth must be enforced
 * via a gRPC interceptor.
 */
public class BasicAuthGrpcInterceptor implements ServerInterceptor {

    private static final String EXPECTED_USERNAME = "testuser";
    private static final String EXPECTED_PASSWORD = "testpass";
    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
        String authHeader = metadata.get(AUTHORIZATION_KEY);
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            call.close(Status.UNAUTHENTICATED.withDescription(
                    "Authentication failed: missing or invalid credentials"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        String base64Credentials = authHeader.substring("Basic ".length());
        String credentials;
        try {
            credentials = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            call.close(Status.UNAUTHENTICATED.withDescription(
                    "Authentication failed: invalid base64 encoding"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        String[] parts = credentials.split(":", 2);
        if (parts.length != 2 || !EXPECTED_USERNAME.equals(parts[0]) || !EXPECTED_PASSWORD.equals(parts[1])) {
            call.close(Status.UNAUTHENTICATED.withDescription(
                    "Authentication failed: invalid credentials"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        return Contexts.interceptCall(Context.current(), call, metadata, next);
    }
}
