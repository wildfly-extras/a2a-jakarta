package org.wildfly.extras.a2a.common;

import java.util.concurrent.Executor;

import jakarta.annotation.Priority;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.a2aproject.sdk.server.util.async.Internal;

@ApplicationScoped
@Alternative
@Priority(20)
public class AsyncManagedExecutorServiceProducer {

    @Resource
    ManagedExecutorService managedExecutor;

    // Injected at deployment time when CDI is available.
    // Instance holds a direct BeanManager reference, so .get() works
    // even on threads without CDI access (e.g. gRPC executor threads).
    @Inject
    Instance<RequestContextController> controllerInstance;

    @Produces
    @Internal
    public Executor produce() {
        return runnable -> managedExecutor.execute(() -> {
            RequestContextController controller = controllerInstance.get();
            controller.activate();
            try {
                runnable.run();
            } finally {
                controller.deactivate();
                controllerInstance.destroy(controller);
            }
        });
    }
}
