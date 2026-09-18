package com.mola.molachat.team.solution;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

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

    @Test
    public void blockedMutationDoesNotBlockQueryAndEventOrderIsPreserved() throws Exception {
        TeamCommandTransport transport = new TeamCommandTransport(4);
        CountDownLatch mutationStarted = new CountDownLatch(1);
        CountDownLatch releaseMutation = new CountDownLatch(1);
        CountDownLatch queryCompleted = new CountDownLatch(1);
        CountDownLatch mutationsCompleted = new CountDownLatch(2);
        CountDownLatch eventsCompleted = new CountDownLatch(2);
        List<String> events = new CopyOnWriteArrayList<>();
        List<String> mutations = new CopyOnWriteArrayList<>();

        transport.dispatchEvent("transport-a",
                TeamCommandTransport.CallbackLane.GATEWAY_MUTATION, ignored -> {
                    mutationStarted.countDown();
                    await(releaseMutation);
                    mutations.add("1");
                    mutationsCompleted.countDown();
                }, Collections.singletonMap("operation", "member"));
        assertTrue(mutationStarted.await(1, TimeUnit.SECONDS));

        transport.dispatchEvent("transport-a",
                TeamCommandTransport.CallbackLane.GATEWAY_QUERY,
                ignored -> queryCompleted.countDown(),
                Collections.singletonMap("operation", "list"));
        transport.dispatchEvent("transport-a",
                TeamCommandTransport.CallbackLane.GATEWAY_MUTATION, ignored -> {
                    mutations.add("2");
                    mutationsCompleted.countDown();
                }, Collections.singletonMap("operation", "create"));
        transport.dispatchEvent("transport-a", ignored -> {
            events.add(ignored.get("id"));
            eventsCompleted.countDown();
        }, Collections.singletonMap("id", "1"));
        transport.dispatchEvent("transport-a", ignored -> {
            events.add(ignored.get("id"));
            eventsCompleted.countDown();
        }, Collections.singletonMap("id", "2"));

        assertTrue("query must not wait for mutation lane",
                queryCompleted.await(1, TimeUnit.SECONDS));
        assertTrue(eventsCompleted.await(1, TimeUnit.SECONDS));
        assertEquals(java.util.Arrays.asList("1", "2"), events);
        releaseMutation.countDown();
        assertTrue(mutationsCompleted.await(1, TimeUnit.SECONDS));
        assertEquals(java.util.Arrays.asList("1", "2"), mutations);
        transport.close();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
