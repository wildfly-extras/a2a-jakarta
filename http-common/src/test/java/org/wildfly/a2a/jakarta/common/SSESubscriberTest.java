package org.wildfly.a2a.jakarta.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SSESubscriberTest {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void synchronousComplete_cancelsHeartbeat() throws Exception {
        CompletableFuture<Void> streamingComplete = new CompletableFuture<>();
        PrintWriter writer = new PrintWriter(new StringWriter());

        SSESubscriber subscriber = new SSESubscriber(streamingComplete, writer, scheduler);

        Flow.Publisher<String> publisher = sub -> {
            sub.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    sub.onComplete();
                }

                @Override
                public void cancel() {
                }
            });
        };

        publisher.subscribe(subscriber);

        assertTrue(streamingComplete.get(2, TimeUnit.SECONDS) == null,
                "streamingComplete should complete normally");
    }

    /**
     * Events written by onNext() and heartbeats written by the scheduler thread must never be
     * written to the response concurrently. Servlet containers do not guarantee a thread-safe
     * PrintWriter (Undertow's bypasses PrintWriter's own lock), and interleaved writes corrupt
     * the HTTP chunked encoding of the SSE stream.
     */
    @Test
    void heartbeatNeverWritesConcurrentlyWithEvents() throws Exception {
        CompletableFuture<Void> streamingComplete = new CompletableFuture<>();
        NonThreadSafeWriter writer = new NonThreadSafeWriter();
        SSESubscriber subscriber = new SSESubscriber(streamingComplete, writer, scheduler);

        AtomicReference<Flow.Subscriber<? super String>> subscriberRef = new AtomicReference<>();
        Flow.Publisher<String> publisher = sub -> {
            subscriberRef.set(sub);
            sub.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                }

                @Override
                public void cancel() {
                }
            });
        };
        publisher.subscribe(subscriber);

        // Keep the event thread writing for longer than two heartbeat intervals (1s each).
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(2500);
        while (System.nanoTime() < deadline && !writer.overlapDetected.get()) {
            subscriberRef.get().onNext("{\"kind\":\"status-update\"}");
        }
        subscriberRef.get().onComplete();

        assertTrue(streamingComplete.get(2, TimeUnit.SECONDS) == null,
                "streamingComplete should complete normally");
        assertTrue(writer.heartbeats.get() > 0, "expected at least one heartbeat to be written");
        assertFalse(writer.overlapDetected.get(), "heartbeat was written concurrently with an event");
    }

    /**
     * Mimics a servlet container PrintWriter that does not synchronize on PrintWriter's lock, and
     * records any overlapping writes. Each write is slowed down to widen the race window.
     */
    private static class NonThreadSafeWriter extends PrintWriter {
        final AtomicBoolean overlapDetected = new AtomicBoolean();
        final AtomicInteger heartbeats = new AtomicInteger();
        private final AtomicInteger inWrite = new AtomicInteger();

        NonThreadSafeWriter() {
            super(new StringWriter());
        }

        @Override
        public void write(String s) {
            if (inWrite.incrementAndGet() > 1) {
                overlapDetected.set(true);
            }
            try {
                if (s.startsWith(":")) {
                    heartbeats.incrementAndGet();
                }
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inWrite.decrementAndGet();
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public boolean checkError() {
            return false;
        }
    }
}
