package org.wildfly.a2a.jakarta.test.common.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class MultiUserBasicAuthGrpcInterceptor implements ServerInterceptor {

    public static final Context.Key<String> AUTHENTICATED_USER_KEY = Context.key("authenticated-user");

    private static final Map<String, String> USERS = Map.of(
            "testuser", "testpass",
            "userB", "passB");

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
        if (parts.length != 2) {
            call.close(Status.UNAUTHENTICATED.withDescription(
                    "Authentication failed: invalid credentials format"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        String expectedPassword = USERS.get(parts[0]);
        if (expectedPassword == null || !MessageDigest.isEqual(
                expectedPassword.getBytes(StandardCharsets.UTF_8),
                parts[1].getBytes(StandardCharsets.UTF_8))) {
            call.close(Status.UNAUTHENTICATED.withDescription(
                    "Authentication failed: invalid credentials"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        Context ctx = Context.current().withValue(AUTHENTICATED_USER_KEY, parts[0]);
        return Contexts.interceptCall(ctx, call, metadata, next);
    }
}
