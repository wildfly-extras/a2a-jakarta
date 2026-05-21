package org.wildfly.extras.a2a.server.apps.grpc.compat03;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

import org.a2aproject.sdk.compat03.conversion.Convert_v0_3_To10RequestHandler;
import org.a2aproject.sdk.compat03.spec.AgentCard_v0_3;
import org.a2aproject.sdk.compat03.spec.DeleteTaskPushNotificationConfigParams_v0_3;
import org.a2aproject.sdk.compat03.spec.EventKind_v0_3;
import org.a2aproject.sdk.compat03.spec.GetTaskPushNotificationConfigParams_v0_3;
import org.a2aproject.sdk.compat03.spec.ListTaskPushNotificationConfigParams_v0_3;
import org.a2aproject.sdk.compat03.spec.MessageSendParams_v0_3;
import org.a2aproject.sdk.compat03.spec.StreamingEventKind_v0_3;
import org.a2aproject.sdk.compat03.spec.TaskIdParams_v0_3;
import org.a2aproject.sdk.compat03.spec.TaskPushNotificationConfig_v0_3;
import org.a2aproject.sdk.compat03.spec.TaskQueryParams_v0_3;
import org.a2aproject.sdk.compat03.spec.Task_v0_3;
import org.a2aproject.sdk.compat03.transport.grpc.handler.CallContextFactory_v0_3;
import org.a2aproject.sdk.compat03.transport.grpc.handler.GrpcHandler_v0_3;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.spec.A2AError;

/**
 * WildFly gRPC Handler for v0.3 compatibility that uses static cache for CDI beans.
 *
 * The WildFly gRPC subsystem instantiates this class directly using
 * reflection and the default constructor, bypassing CDI completely.
 *
 * Since CDI is not available on gRPC threads, we use static cache
 * populated during application startup when CDI is available.
 */
public class WildFlyGrpcHandler_v0_3 extends GrpcHandler_v0_3 {

    // Static cache populated during application startup by GrpcBeanInitializer_v0_3
    private static volatile AgentCard_v0_3 staticAgentCard;
    private static volatile Convert_v0_3_To10RequestHandler staticRequestHandler;
    private static volatile CallContextFactory_v0_3 staticCallContextFactory;
    private static volatile Executor staticExecutor;
    private static volatile ClassLoader deploymentClassLoader;

    public WildFlyGrpcHandler_v0_3() {
        // Default constructor - the only one used by WildFly gRPC subsystem
    }

    /**
     * Called by GrpcBeanInitializer_v0_3 during CDI initialization to cache beans
     * for use by gRPC threads where CDI is not available.
     */
    static void setStaticBeans(AgentCard_v0_3 agentCard, Convert_v0_3_To10RequestHandler requestHandler, CallContextFactory_v0_3 callContextFactory, Executor executor, ClassLoader classLoader) {
        staticAgentCard = agentCard;
        staticRequestHandler = requestHandler;
        staticCallContextFactory = callContextFactory;
        staticExecutor = executor;
        deploymentClassLoader = classLoader;
        // Since GrpcHandler_v0_3 uses a private requestHandler field directly,
        // we cannot rely on overriding getRequestHandler(). Instead, we don't
        // call setRequestHandler() here because we are in a static context.
        // The instance-level init is handled lazily via ensureInitialized().
    }

    /**
     * Ensures the instance is initialized with the static beans.
     * Called lazily since setStaticBeans() is static but setRequestHandler() is an instance method.
     */
    private void ensureInitialized() {
        if (getRequestHandler() == null && staticRequestHandler != null) {
            setRequestHandler(new ClassLoaderSwitchingRequestHandler(staticRequestHandler, deploymentClassLoader));
        }
    }

    @Override
    protected AgentCard_v0_3 getAgentCard() {
        ensureInitialized();
        if (staticAgentCard == null) {
            throw new RuntimeException("AgentCard not available. ApplicationStartup may not have run yet.");
        }
        return staticAgentCard;
    }

    @Override
    protected CallContextFactory_v0_3 getCallContextFactory() {
        ensureInitialized();
        return staticCallContextFactory; // Can be null if not configured
    }

    @Override
    protected Executor getExecutor() {
        ensureInitialized();
        if (staticExecutor == null) {
            throw new RuntimeException("Executor not available. ApplicationStartup may not have run yet.");
        }
        return staticExecutor;
    }

    /**
     * RequestHandler wrapper that sets the deployment classloader as TCCL before delegating.
     * This is necessary because gRPC threads have the grpc extension module classloader,
     * which cannot see deployment WEB-INF/lib jars needed by ServiceLoader.
     *
     * Extends Convert_v0_3_To10RequestHandler so it can be set via setRequestHandler()
     * and used by GrpcHandler_v0_3's private requestHandler field.
     */
    private static class ClassLoaderSwitchingRequestHandler extends Convert_v0_3_To10RequestHandler {
        private final Convert_v0_3_To10RequestHandler delegate;
        private final ClassLoader deploymentClassLoader;

        ClassLoaderSwitchingRequestHandler(Convert_v0_3_To10RequestHandler delegate, ClassLoader deploymentClassLoader) {
            super(null); // Super's v10Handler is unused since we override all methods
            this.delegate = delegate;
            this.deploymentClassLoader = deploymentClassLoader;
        }

        private <T> T withDeploymentClassLoader(java.util.function.Supplier<T> supplier) {
            ClassLoader originalTCCL = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(deploymentClassLoader);
                return supplier.get();
            } finally {
                Thread.currentThread().setContextClassLoader(originalTCCL);
            }
        }

        @Override
        public EventKind_v0_3 onMessageSend(MessageSendParams_v0_3 params, ServerCallContext context) throws A2AError {
            try {
                return withDeploymentClassLoader(() -> {
                    try {
                        return delegate.onMessageSend(params, context);
                    } catch (A2AError e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (RuntimeException e) {
                throwIfA2AError(e);
                throw e;
            }
        }

        @Override
        public Flow.Publisher<StreamingEventKind_v0_3> onMessageSendStream(MessageSendParams_v0_3 params, ServerCallContext context) throws A2AError {
            try {
                return withDeploymentClassLoader(() -> {
                    try {
                        return delegate.onMessageSendStream(params, context);
                    } catch (A2AError e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (RuntimeException e) {
                throwIfA2AError(e);
                throw e;
            }
        }

        @Override
        public Task_v0_3 onGetTask(TaskQueryParams_v0_3 params, ServerCallContext context) throws A2AError {
            try {
                return withDeploymentClassLoader(() -> {
                    try {
                        return delegate.onGetTask(params, context);
                    } catch (A2AError e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (RuntimeException e) {
                throwIfA2AError(e);
                throw e;
            }
        }

        @Override
        public Task_v0_3 onCancelTask(TaskIdParams_v0_3 params, ServerCallContext context) throws A2AError {
            try {
                return withDeploymentClassLoader(() -> {
                    try {
                        return delegate.onCancelTask(params, context);
                    } catch (A2AError e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (RuntimeException e) {
                throwIfA2AError(e);
                throw e;
            }
        }

        @Override
        public TaskPushNotificationConfig_v0_3 onSetTaskPushNotificationConfig(TaskPushNotificationConfig_v0_3 config, ServerCallContext context) throws A2AError {
            try {
                return withDeploymentClassLoader(() -> {
                    try {
                        return delegate.onSetTaskPushNotificationConfig(config, context);
                    } catch (A2AError e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (RuntimeException e) {
                throwIfA2AError(e);
                throw e;
            }
        }

        @Override
        public TaskPushNotificationConfig_v0_3 onGetTaskPushNotificationConfig(GetTaskPushNotificationConfigParams_v0_3 params, ServerCallContext context) throws A2AError {
            try {
                return withDeploymentClassLoader(() -> {
                    try {
                        return delegate.onGetTaskPushNotificationConfig(params, context);
                    } catch (A2AError e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (RuntimeException e) {
                throwIfA2AError(e);
                throw e;
            }
        }

        @Override
        public Flow.Publisher<StreamingEventKind_v0_3> onResubscribeToTask(TaskIdParams_v0_3 params, ServerCallContext context) throws A2AError {
            try {
                return withDeploymentClassLoader(() -> {
                    try {
                        return delegate.onResubscribeToTask(params, context);
                    } catch (A2AError e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (RuntimeException e) {
                throwIfA2AError(e);
                throw e;
            }
        }

        @Override
        public List<TaskPushNotificationConfig_v0_3> onListTaskPushNotificationConfig(ListTaskPushNotificationConfigParams_v0_3 params, ServerCallContext context) throws A2AError {
            try {
                return withDeploymentClassLoader(() -> {
                    try {
                        return delegate.onListTaskPushNotificationConfig(params, context);
                    } catch (A2AError e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (RuntimeException e) {
                throwIfA2AError(e);
                throw e;
            }
        }

        @Override
        public void onDeleteTaskPushNotificationConfig(DeleteTaskPushNotificationConfigParams_v0_3 params, ServerCallContext context) throws A2AError {
            try {
                withDeploymentClassLoader(() -> {
                    try {
                        delegate.onDeleteTaskPushNotificationConfig(params, context);
                        return null;
                    } catch (A2AError e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (RuntimeException e) {
                throwIfA2AError(e);
                throw e;
            }
        }

        private static void throwIfA2AError(RuntimeException e) throws A2AError {
            if (e.getCause() instanceof A2AError a2aError) {
                throw a2aError;
            }
        }
    }
}
