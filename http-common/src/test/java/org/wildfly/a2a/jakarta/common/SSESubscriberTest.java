package org.wildfly.a2a.jakarta.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

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
     * Completion must wait for an active heartbeat write before allowing the request thread to
     * close the writer. Observe completion blocked in stopWriting() on the heartbeat's monitor
     * before checking that the stream is still pending, then reject all writes after completion.
     */
    @Test
    void completionWaitsForRunningHeartbeatAndPreventsFurtherWrites() throws Exception {
        ScheduledExecutorService heartbeatScheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> heartbeatFuture = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.forClass(Runnable.class);
        doReturn(heartbeatFuture).when(heartbeatScheduler).scheduleAtFixedRate(
                heartbeat.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));

        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch releaseWrite = new CountDownLatch(1);
        AtomicReference<Thread> heartbeatThread = new AtomicReference<>();
        AtomicInteger writeAttempts = new AtomicInteger();
        PrintWriter writer = new PrintWriter(new StringWriter()) {
            @Override
            public void write(String value) {
                writeAttempts.incrementAndGet();
                heartbeatThread.set(Thread.currentThread());
                writeStarted.countDown();
                try {
                    assertTrue(releaseWrite.await(5, TimeUnit.SECONDS), "heartbeat write was not released");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                super.write(value);
            }
        };
        CompletableFuture<Void> streamingComplete = new CompletableFuture<>();
        SSESubscriber subscriber = new SSESubscriber(streamingComplete, writer, heartbeatScheduler);
        subscriber.onSubscribe(mock(Flow.Subscription.class));

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> heartbeatWrite = workers.submit(heartbeat.getValue());
            assertTrue(writeStarted.await(2, TimeUnit.SECONDS), "heartbeat did not enter write()");

            CountDownLatch completionStarted = new CountDownLatch(1);
            AtomicReference<Thread> completionThread = new AtomicReference<>();
            Future<?> completion = workers.submit(() -> {
                completionThread.set(Thread.currentThread());
                completionStarted.countDown();
                subscriber.onComplete();
            });
            assertTrue(completionStarted.await(2, TimeUnit.SECONDS), "completion did not start");
            awaitBlockedOnHeartbeatWrite(completionThread.get(), heartbeatThread.get());
            assertFalse(streamingComplete.isDone(),
                    "streamingComplete must wait until the running heartbeat finishes writing");

            releaseWrite.countDown();
            heartbeatWrite.get(2, TimeUnit.SECONDS);
            completion.get(2, TimeUnit.SECONDS);
            streamingComplete.get(2, TimeUnit.SECONDS);
            // The REST request thread closes the writer after streamingComplete completes.
            writer.close();

            // A heartbeat already dispatched before cancellation must also respect completion.
            heartbeat.getValue().run();
            subscriber.onNext("{\"kind\":\"status-update\"}");
            assertEquals(1, writeAttempts.get(), "no writer access is allowed after completion");
        } finally {
            releaseWrite.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS), "writer threads did not stop");
        }
    }

    private static void awaitBlockedOnHeartbeatWrite(Thread completionThread, Thread heartbeatThread)
            throws InterruptedException {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            ThreadInfo info = threads.getThreadInfo(completionThread.getId(), 1);
            if (info != null && info.getThreadState() == Thread.State.BLOCKED
                    && info.getLockOwnerId() == heartbeatThread.getId()) {
                StackTraceElement frame = info.getStackTrace()[0];
                if (frame.getClassName().equals(SSESubscriber.class.getName())
                        && frame.getMethodName().equals("stopWriting")) {
                    return;
                }
            }
            Thread.sleep(1);
        }
        fail("completion did not block in stopWriting() on the running heartbeat's write lock");
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
