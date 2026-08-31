package com.mola.molachat.team.solution;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TeamCommandTransportTest {

    @Test
    public void callbackIngressReturnsBeforeSlowHandlerAndPreservesTransportOrder()
            throws Exception {
        TeamCommandTransport transport = new TeamCommandTransport(4);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        List<String> handled = new CopyOnWriteArrayList<>();

        long startedAt = System.nanoTime();
        transport.dispatchEvent("transport-a", event -> {
            firstStarted.countDown();
            await(releaseFirst);
            handled.add(event.get("id"));
            completed.countDown();
        }, Collections.singletonMap("id", "1"));
        assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
        transport.dispatchEvent("transport-a", event -> {
            handled.add(event.get("id"));
            completed.countDown();
        }, Collections.singletonMap("id", "2"));
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt) < 500L);

        releaseFirst.countDown();
        assertTrue(completed.await(1, TimeUnit.SECONDS));
        assertEquals(java.util.Arrays.asList("1", "2"), handled);
        transport.close();
    }

    @Test
    public void fullQueueAndClosedTransportRejectInsteadOfSilentlyDropping() throws Exception {
        TeamCommandTransport transport = new TeamCommandTransport(1);
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        transport.dispatchEvent("transport-a", ignored -> {
            running.countDown();
            await(release);
        }, Collections.emptyMap());
        assertTrue(running.await(1, TimeUnit.SECONDS));
        transport.dispatchEvent("transport-a", ignored -> { }, Collections.emptyMap());
        try {
            transport.dispatchEvent("transport-a", ignored -> { }, Collections.emptyMap());
            fail("full callback queue must reject so the sender can retry");
        } catch (RejectedExecutionException expected) {
            // expected
        }
        release.countDown();
        transport.close();
        try {
            transport.dispatchEvent("transport-a", ignored -> { }, Collections.emptyMap());
            fail("closed callback transport must reject");
        } catch (RejectedExecutionException expected) {
            // expected
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
