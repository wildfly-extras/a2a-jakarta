package org.wildfly.extras.a2a.grpc.compat03;

import org.a2aproject.sdk.compat03.common.A2AHeaders_v0_3;
import org.a2aproject.sdk.compat03.transport.grpc.context.GrpcContextKeys_v0_3;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * gRPC server interceptor that extracts A2A protocol v0.3 headers from request
 * metadata and stores them in the gRPC {@link Context} for use by
 * {@link org.a2aproject.sdk.compat03.transport.grpc.handler.GrpcHandler_v0_3}.
 *
 * <p>WildFly's gRPC subsystem discovers {@link ServerInterceptor} implementations
 * in the deployment and applies them automatically.
 */
public class A2AExtensionsInterceptor_v0_3 implements ServerInterceptor {

    private static final Metadata.Key<String> EXTENSIONS_KEY = Metadata.Key.of(
            A2AHeaders_v0_3.X_A2A_EXTENSIONS.toLowerCase(), Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
        String extensions = metadata.get(EXTENSIONS_KEY);

        // Create enhanced context with rich information (equivalent to Python's ServicerContext)
        Context context = Context.current()
                // Store complete metadata for full header access
                .withValue(GrpcContextKeys_v0_3.METADATA_KEY, metadata)
                // Store method name (equivalent to Python's context.method())
                .withValue(GrpcContextKeys_v0_3.METHOD_NAME_KEY, call.getMethodDescriptor().getFullMethodName())
                // Store peer information for client connection details
                .withValue(GrpcContextKeys_v0_3.PEER_INFO_KEY, getPeerInfo(call));

        // Store A2A extensions if present
        if (extensions != null) {
            context = context.withValue(GrpcContextKeys_v0_3.EXTENSIONS_HEADER_KEY, extensions);
        }

        return Contexts.interceptCall(context, call, metadata, next);
    }

    /**
     * Safely extracts peer information from the ServerCall.
     *
     * @param serverCall the gRPC ServerCall
     * @return peer information string, or "unknown" if not available
     */
    private String getPeerInfo(ServerCall<?, ?> serverCall) {
        try {
            Object remoteAddr = serverCall.getAttributes().get(io.grpc.Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
            return remoteAddr != null ? remoteAddr.toString() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
