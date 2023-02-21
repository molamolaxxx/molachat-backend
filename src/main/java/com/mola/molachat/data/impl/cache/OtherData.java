package com.mola.molachat.data.impl.cache;

import com.mola.molachat.condition.CacheCondition;
import com.mola.molachat.data.OtherDataInterface;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 其他个性化数据存储
 * @date : 2023-02-21 11:59
 **/
@Component
@Conditional(CacheCondition.class)
@Slf4j
public class OtherData implements OtherDataInterface {

    /**
     * 有效的gpt3子序列
     */
    protected Set<String> availableChildApiKeys = new ConcurrentSkipListSet<>();

    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    @Override
    public Set<String> getGpt3ChildTokens() {
        readLock.lock();
        try {
            return availableChildApiKeys;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void operateGpt3ChildTokens(Consumer<Set<String>> operate) {
        writeLock.lock();
        try {
            operate.accept(availableChildApiKeys);
        }finally {
            writeLock.unlock();
        }
    }
}
