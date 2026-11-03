/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.a2a.jakarta.common;

import java.io.PrintWriter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.a2aproject.sdk.server.util.sse.SseFormatter;

public class SSESubscriber implements Flow.Subscriber<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SSESubscriber.class);

    private static final long HEARTBEAT_INTERVAL_MS = 1000;

    // Hook so testing can wait until the async Subscription is subscribed.
    private static volatile Runnable streamingIsSubscribedRunnable;

    private Flow.Subscription subscription;
    private volatile boolean disconnected;
    private ScheduledFuture<?> heartbeatFuture;

    private final ScheduledExecutorService heartbeatScheduler;
    private final AtomicLong eventId = new AtomicLong(0);
    private final CompletableFuture<Void> streamingComplete;
    private final PrintWriter writer;

    // Serializes all access to the writer between the EventConsumer thread (onNext) and the
    // heartbeat thread. The servlet container's PrintWriter is not guaranteed to be thread-safe:
    // Undertow's ServletPrintWriterDelegate bypasses PrintWriter's own lock, so unsynchronized
    // concurrent writes corrupt the HTTP chunked encoding of the response.
    private final Object writeLock = new Object();
    // Guarded by writeLock. Once set, no further writes are attempted, so the request thread may
    // safely close the writer after streamingComplete is completed.
    private boolean closed;

    public SSESubscriber(CompletableFuture<Void> streamingComplete, PrintWriter writer, ScheduledExecutorService heartbeatScheduler) {
        this.streamingComplete = streamingComplete;
        this.writer = writer;
        this.heartbeatScheduler = heartbeatScheduler;
    }

    public static void setStreamingIsSubscribedRunnable(Runnable streamingIsSubscribedRunnable) {
        SSESubscriber.streamingIsSubscribedRunnable = streamingIsSubscribedRunnable;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        LOGGER.info("SSE onSubscribe: streaming started on thread={}", Thread.currentThread().getName());
        this.subscription = subscription;
        // Schedule heartbeat BEFORE requesting events: request() may trigger
        // synchronous onComplete, which calls stopWriting() — the future
        // must already be assigned so it can be cancelled.
        heartbeatFuture = heartbeatScheduler.scheduleAtFixedRate(
                this::heartbeat, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // Request all events upfront (mirrors the a2a-java reference SseResponseWriter, see
        // a2a-java #906): a single-item demand window drops back-to-back emissions from the
        // EventConsumer. The EventConsumer's internal buffer (256 items) is the only bound, and
        // EventConsumer.BUFFER_FLUSH_DELAY_MS guarantees the final write is flushed before
        // onComplete fires — so write-level backpressure via request(1) is neither needed nor safe.
        subscription.request(Long.MAX_VALUE);

        // Notify tests that we are subscribed
        Runnable runnable = streamingIsSubscribedRunnable;
        if (runnable != null) {
            runnable.run();
        }
    }

    @Override
    public void onNext(String item) {
        if (disconnected) {
            LOGGER.info("SSE onNext: ignoring event (already disconnected)");
            return;
        }
        try {
            long id = eventId.getAndIncrement();
            String sseEvent = SseFormatter.formatJsonAsSSE(item, id);

            boolean failed;
            synchronized (writeLock) {
                if (closed) {
                    return;
                }
                writer.write(sseEvent);
                writer.flush();
                failed = writer.checkError();
            }

            if (failed) {
                LOGGER.info("SSE onNext: write failed for event id={} (client disconnect)", id);
                handleClientDisconnect();
                return;
            }

            if (LOGGER.isDebugEnabled()) {
                String preview = item.length() > 200 ? item.substring(0, 200) + "..." : item;
                LOGGER.debug("SSE onNext: event id={} sent, preview={}", id, preview);
            }
        } catch (Exception e) {
            LOGGER.error("Error writing SSE event: {}", e.getMessage(), e);
            onError(e);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        LOGGER.info("SSE onError: {} on thread={}", throwable.getMessage(), Thread.currentThread().getName());
        stopWriting();
        if (subscription != null) {
            subscription.cancel();
        }
        streamingComplete.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        LOGGER.info("SSE onComplete: stream finished normally on thread={}", Thread.currentThread().getName());
        stopWriting();
        streamingComplete.complete(null);
    }

    public void handleClientDisconnect() {
        if (disconnected) {
            return;
        }
        disconnected = true;
        stopWriting();
        LOGGER.info("SSE handleClientDisconnect: client disconnected, cancelling subscription on thread={}",
                Thread.currentThread().getName());
        if (subscription != null) {
            subscription.cancel();
        }
        // Do NOT cancel the EventConsumer here: the client may resubscribe, and the
        // agent executor must keep running so events continue to buffer in the TaskStore.
        // Task-level timeouts in the SDK handle cleanup if the client never comes back.
        streamingComplete.complete(null);
    }

    /**
     * Cancels the heartbeat and stops all further writes. Once this returns, no write is in
     * progress and none will start, so the writer can be closed safely. Cancelling the heartbeat
     * future alone is not enough, as {@code cancel(false)} does not wait for a running heartbeat.
     */
    private void stopWriting() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
        }
        synchronized (writeLock) {
            closed = true;
        }
    }

    private void heartbeat() {
        if (disconnected) {
            return;
        }
        boolean failed;
        synchronized (writeLock) {
            if (closed) {
                return;
            }
            // An SSE comment (": ...\n\n") is a complete message; writeLock guarantees it is
            // never interleaved with an event written by onNext().
            writer.write(": ping\n\n");
            writer.flush();
            failed = writer.checkError();
        }
        if (failed) {
            LOGGER.info("SSE heartbeat detected client disconnect");
            handleClientDisconnect();
        }
    }
}
