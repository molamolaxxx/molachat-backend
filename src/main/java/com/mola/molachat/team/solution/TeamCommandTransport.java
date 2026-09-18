package com.mola.molachat.team.solution;

import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.cmd.proxy.client.resp.CmdInvokeResponse;
import com.mola.cmd.proxy.client.resp.CmdResponseContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

@Component
public class TeamCommandTransport {

    enum CallbackLane {
        EVENT,
        GATEWAY_QUERY,
        GATEWAY_MUTATION
    }

    static final class CommandTimeoutException extends RuntimeException {
        CommandTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(TeamCommandTransport.class);
    private static final int DEFAULT_EVENT_QUEUE_CAPACITY = 1024;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 2L;

    private final int eventQueueCapacity;
    private final Map<String, ThreadPoolExecutor> eventExecutors = new ConcurrentHashMap<>();
    private final AtomicInteger eventThreadSequence = new AtomicInteger();
    private volatile boolean closed;

    public TeamCommandTransport() {
        this(DEFAULT_EVENT_QUEUE_CAPACITY);
    }

    TeamCommandTransport(int eventQueueCapacity) {
        if (eventQueueCapacity < 1) {
            throw new IllegalArgumentException("eventQueueCapacity must be positive");
        }
        this.eventQueueCapacity = eventQueueCapacity;
    }

    public Map<String, String> send(String command, String transportGroup, String payload) {
        return send(command, transportGroup, new String[]{payload});
    }

    public Map<String, String> send(String command, String transportGroup, String payload,
                                    long timeoutMillis) {
        try {
            CmdInvokeResponse<CmdResponseContent> response = CmdSender.INSTANCE.sendWithTimeout(
                    command, transportGroup, new String[]{payload}, timeoutMillis);
            return resultMap(response);
        } catch (RuntimeException failure) {
            if (isTimeoutFailure(failure)) {
                throw new CommandTimeoutException("cmdproxy command timed out after "
                        + timeoutMillis + "ms", failure);
            }
            throw failure;
        }
    }

    public Map<String, String> sendWithoutArgs(String command, String transportGroup) {
        return send(command, transportGroup, new String[0]);
    }

    private Map<String, String> send(String command, String transportGroup, String[] args) {
        CmdInvokeResponse<CmdResponseContent> response =
                CmdSender.INSTANCE.send(command, transportGroup, args);
        return resultMap(response);
    }

    private Map<String, String> resultMap(CmdInvokeResponse<CmdResponseContent> response) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            return null;
        }
        return response.getData().getResultMap();
    }

    public void registerEventCallback(String eventCommand, String transportGroup,
                                      Consumer<Map<String, String>> callback) {
        registerEventCallback(eventCommand, transportGroup,
                ignored -> CallbackLane.EVENT, callback);
    }

    void registerEventCallback(String eventCommand, String transportGroup,
                               Function<Map<String, String>, CallbackLane> laneSelector,
                               Consumer<Map<String, String>> callback) {
        CmdSender.INSTANCE.registerCallback(eventCommand, transportGroup, response -> {
            Map<String, String> resultMap = response.getResultMap();
            dispatchEvent(transportGroup, laneSelector.apply(resultMap), callback, resultMap);
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * RPC callback 线程只负责复制并入队，不能同步进入 Team 状态机。
     * 每个 transport 使用独立单线程有界队列，保证同一 participant 事件有序；
     * 队列满或组件关闭时显式拒绝，使 cmd-proxy 的 callback retry 能感知失败。
     */
    void dispatchEvent(String transportGroup, Consumer<Map<String, String>> callback,
                       Map<String, String> resultMap) {
        dispatchEvent(transportGroup, CallbackLane.EVENT, callback, resultMap);
    }

    void dispatchEvent(String transportGroup, CallbackLane lane,
                       Consumer<Map<String, String>> callback,
                       Map<String, String> resultMap) {
        Map<String, String> immutableEvent = Collections.unmodifiableMap(
                resultMap == null ? Collections.emptyMap() : new HashMap<>(resultMap));
        long enqueuedAt = System.nanoTime();
        ThreadPoolExecutor executor = eventExecutor(transportGroup, lane);
        int queueSize = executor.getQueue().size();
        String requestId = immutableEvent.get("requestId");
        String operation = immutableEvent.get("operation");
        try {
            executor.execute(() -> {
                long startedAt = System.nanoTime();
                long queueDelayMillis = TimeUnit.NANOSECONDS.toMillis(startedAt - enqueuedAt);
                try {
                    callback.accept(immutableEvent);
                } catch (RuntimeException handlerFailure) {
                    log.error("Fast Team callback failed, transportGroup={}, lane={},"
                                    + " requestId={}, operation={}",
                            transportGroup, lane, requestId, operation, handlerFailure);
                } finally {
                    long durationMillis = TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - startedAt);
                    log.info("Fast Team callback completed, transportGroup={}, lane={},"
                                    + " requestId={}, operation={}, queueDelayMs={},"
                                    + " durationMs={}, queueSize={}",
                            transportGroup, lane, requestId, operation, queueDelayMillis,
                            durationMillis, executor.getQueue().size());
                }
            });
            log.debug("Fast Team callback enqueued, transportGroup={}, lane={},"
                            + " requestId={}, operation={}, queueSize={}",
                    transportGroup, lane, requestId, operation, queueSize + 1);
        } catch (RejectedExecutionException rejected) {
            log.error("Fast Team callback queue rejected, transportGroup={}, lane={},"
                            + " requestId={}, operation={}, queueSize={}",
                    transportGroup, lane, requestId, operation, queueSize, rejected);
            throw rejected;
        }
    }

    private synchronized ThreadPoolExecutor eventExecutor(String transportGroup,
                                                          CallbackLane lane) {
        if (closed) {
            throw new RejectedExecutionException("Fast Team event transport is closed");
        }
        String executorKey = transportGroup + "#" + lane.name();
        return eventExecutors.computeIfAbsent(executorKey,
                ignored -> newEventExecutor(lane));
    }

    private ThreadPoolExecutor newEventExecutor(CallbackLane lane) {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable,
                    "fast-team-" + lane.name().toLowerCase().replace('_', '-') + "-"
                            + eventThreadSequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(eventQueueCapacity), threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    private boolean isTimeoutFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("provider time out")
                    || message.contains("timed out") || message.contains("timeout"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @PreDestroy
    public synchronized void close() {
        closed = true;
        for (ThreadPoolExecutor executor : eventExecutors.values()) {
            executor.shutdown();
        }
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(SHUTDOWN_TIMEOUT_SECONDS);
        for (ThreadPoolExecutor executor : eventExecutors.values()) {
            try {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L
                        || !executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        eventExecutors.clear();
    }
}
