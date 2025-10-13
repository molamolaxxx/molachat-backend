package com.mola.molachat.robot.model;

import lombok.extern.slf4j.Slf4j;

import java.util.Deque;
import java.util.LinkedList;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2025-10-11 23:12
 **/
@Slf4j
public class InvokeLimiter {

    private int requestPerMin;

    private Deque<Long> interval;

    public InvokeLimiter(int requestPerMin) {
        this.requestPerMin = requestPerMin;
        this.interval = new LinkedList<>();
    }

    public synchronized void tryAcquire() {
        if (requestPerMin <= 0) {
            return;
        }
        log.info("[InvokeLimiter] 开始执行, interval = {}, requestPerMin = {}", interval, requestPerMin);
        if (interval.size() == requestPerMin) {
            // 计算还需等待的时间
            long duringTime = System.currentTimeMillis() - interval.getFirst();
            try {
                long waitTime = 61000 - duringTime;
                if (waitTime > 0) {
                    log.info("[InvokeLimiter] 命中限流，剩余时间 : {}", waitTime);
                    Thread.sleep(waitTime);
                }
            } catch (InterruptedException ignore) {
            }
            interval.pollFirst();
        }
        interval.addLast(System.currentTimeMillis());
    }
}
