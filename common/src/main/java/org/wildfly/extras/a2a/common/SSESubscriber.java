/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extras.a2a.common;

import java.io.PrintWriter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.util.sse.SseFormatter;

public class SSESubscriber implements Flow.Subscriber<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SSESubscriber.class);
    // Hook so testing can wait until the async Subscription is subscribed.
    private static volatile Runnable streamingIsSubscribedRunnable;

    private Flow.Subscription subscription;

    private final AtomicLong eventId = new AtomicLong(0);
    private final CompletableFuture<Void> streamingComplete;
    private final PrintWriter writer;
    private final ServerCallContext context;

    public SSESubscriber(CompletableFuture<Void> streamingComplete, PrintWriter writer, ServerCallContext context) {
        this.streamingComplete = streamingComplete;
        this.writer = writer;
        this.context = context;
    }

    public static void setStreamingIsSubscribedRunnable(Runnable streamingIsSubscribedRunnable) {
        SSESubscriber.streamingIsSubscribedRunnable = streamingIsSubscribedRunnable;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        LOGGER.debug("Custom SSE subscriber onSubscribe called");
        this.subscription = subscription;
        // Use backpressure: request one item at a time
        subscription.request(1);

        // Notify tests that we are subscribed
        Runnable runnable = streamingIsSubscribedRunnable;
        if (runnable != null) {
            runnable.run();
        }
    }

    @Override
    public void onNext(String item) {
        LOGGER.debug("Custom SSE subscriber onNext called with item: {}", item);
        try {
            // Format as proper SSE event using centralized SseFormatter
            long id = eventId.getAndIncrement();
            String sseEvent = SseFormatter.formatJsonAsSSE(item, id);

            writer.write(sseEvent);
            writer.flush();

            // Check if write failed (client disconnected)
            // PrintWriter doesn't throw IOException, so we must check for errors explicitly
            if (writer.checkError()) {
                LOGGER.info("SSE write failed (likely client disconnect)");
                handleClientDisconnect();
                return;
            }

            LOGGER.info("Custom SSE event sent successfully with id: {}", id);

            // Request next item (backpressure)
            subscription.request(1);
        } catch (Exception e) {
            LOGGER.error("Error writing SSE event: {}", e.getMessage(), e);
            onError(e);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        LOGGER.debug("Custom SSE subscriber onError called: {}", throwable.getMessage(), throwable);
        handleClientDisconnect();
        streamingComplete.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        LOGGER.debug("Custom SSE subscriber onComplete called");
        streamingComplete.complete(null);
    }

    private void handleClientDisconnect() {
        LOGGER.debug("SSE connection closed, calling EventConsumer.cancel() to stop polling loop");
        // Cancel subscription to stop receiving events
        if (subscription != null) {
            subscription.cancel();
        }
        // Call EventConsumer cancel callback to clean up ChildQueue
        context.invokeEventConsumerCancelCallback();
    }
}
