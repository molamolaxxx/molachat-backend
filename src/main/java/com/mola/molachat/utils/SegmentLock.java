package com.mola.molachat.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 分段锁，基于重入锁实现更细粒度的并发控制
 * @date : 2021-05-27 21:26
 **/
public class SegmentLock<T> {

    /**
     * 默认预先创建的锁数量.
     */
    private int DEFAULT_LOCK_COUNT = 20;

    private final ConcurrentHashMap<Integer, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    public SegmentLock() {
        init(null, false);
    }

    public SegmentLock(Integer count, boolean isFair) {
        init(count, isFair);
    }

    private void init(Integer count, boolean isFair) {
        if (count != null && count != 0) {
            this.DEFAULT_LOCK_COUNT = count;
        }
        // 预先初始化指定数量的锁
        for (int i = 0; i < this.DEFAULT_LOCK_COUNT; i++) {
            this.lockMap.put(i, new ReentrantLock(isFair));
        }
    }

    public ReentrantLock get(T key) {
        return this.lockMap.get((key.hashCode() >>> 1) % DEFAULT_LOCK_COUNT);
    }

    public void lock(T key) {
        ReentrantLock lock = this.get(key);
        lock.lock();
    }

    public void unlock(T key) {
        ReentrantLock lock = this.get(key);
        lock.unlock();
    }

}
