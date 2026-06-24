package org.wildfly.a2a.jakarta.test.common.auth;

import static org.a2aproject.sdk.server.ServerCallContext.TRANSPORT_KEY;

import java.util.HashMap;
import java.util.Map;

import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.stub.StreamObserver;
import org.a2aproject.sdk.server.auth.AuthenticatedUser;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.spec.TransportProtocol;

public final class GrpcCallContextHelper {

    private GrpcCallContextHelper() {
    }

    public static User resolveUser() {
        String username = MultiUserBasicAuthGrpcInterceptor.AUTHENTICATED_USER_KEY.get();
        return (username != null)
                ? new AuthenticatedUser(username)
                : UnauthenticatedUser.INSTANCE;
    }

    public static <V> Map<String, Object> buildState(
            StreamObserver<V> responseObserver,
            Context.Key<Metadata> metadataKey,
            Context.Key<String> methodNameKey,
            Context.Key<String> peerInfoKey) {
        Map<String, Object> state = new HashMap<>();
        state.put(TRANSPORT_KEY, TransportProtocol.GRPC);
        state.put("grpc_response_observer", responseObserver);

        Context currentContext = Context.current();
        state.put("grpc_context", currentContext);
        Metadata grpcMetadata = metadataKey.get(currentContext);
        if (grpcMetadata != null) {
            state.put("grpc_metadata", grpcMetadata);
            Map<String, String> headers = new HashMap<>();
            for (String key : grpcMetadata.keys()) {
                if (key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                    continue;
                }
                headers.put(key, grpcMetadata.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)));
            }
            state.put("headers", headers);
        }
        String methodName = methodNameKey.get(currentContext);
        if (methodName != null) {
            state.put("grpc_method_name", methodName);
        }
        String peerInfo = peerInfoKey.get(currentContext);
        if (peerInfo != null) {
            state.put("grpc_peer_info", peerInfo);
        }

        return state;
    }
}
