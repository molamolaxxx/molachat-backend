package com.mola.molachat.robot.handler.impl.mcp;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 命令确认管理器，管理服务端阻塞等待用户确认的逻辑
 *
 * @author : molamola
 * @date : 2026-02-24
 **/
@Slf4j
public class CmdConfirmManager {

    public static final CmdConfirmManager INSTANCE = new CmdConfirmManager();

    /**
     * confirmId -> CountDownLatch，用于阻塞等待用户确认
     */
    private final Map<String, CountDownLatch> latchMap = new ConcurrentHashMap<>();

    /**
     * confirmId -> 用户确认结果，true=执行，false=拒绝
     */
    private final Map<String, Boolean> resultMap = new ConcurrentHashMap<>();

    private CmdConfirmManager() {}

    /**
     * 等待用户确认，阻塞当前线程
     * @param confirmId 确认请求ID
     * @param timeoutSeconds 超时时间（秒），超时视为拒绝
     * @return true=用户确认执行，false=用户拒绝或超时
     */
    public boolean waitForConfirm(String confirmId, int timeoutSeconds) {
        CountDownLatch latch = new CountDownLatch(1);
        latchMap.put(confirmId, latch);
        try {
            boolean completed = latch.await(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("命令确认超时，confirmId={}", confirmId);
                return false;
            }
            return resultMap.getOrDefault(confirmId, false);
        } catch (InterruptedException e) {
            log.warn("命令确认等待被中断，confirmId={}", confirmId);
            Thread.currentThread().interrupt();
            return false;
        } finally {
            latchMap.remove(confirmId);
            resultMap.remove(confirmId);
        }
    }

    /**
     * 用户提交确认结果
     * @param confirmId 确认请求ID
     * @param confirmed true=执行，false=拒绝
     */
    public void confirm(String confirmId, boolean confirmed) {
        resultMap.put(confirmId, confirmed);
        CountDownLatch latch = latchMap.get(confirmId);
        if (latch != null) {
            latch.countDown();
        } else {
            log.warn("未找到对应的确认请求，confirmId={}", confirmId);
        }
    }
}
