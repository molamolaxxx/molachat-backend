package com.mola.molachat.common.lock;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @Author: molamola
 * @Date: 19-9-14 上午12:14
 * @Version 1.0
 * 上传文件锁
 */
@Component
public class FileUploadLock {

    /**
     * 文件读写锁
     */
    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private ReentrantReadWriteLock.ReadLock readLock;

    private ReentrantReadWriteLock.WriteLock writeLock;

    @PostConstruct
    public void initLock() {
        readLock = lock.readLock();
        writeLock = lock.writeLock();
    }

    /**
     * 加锁
     */
    public void writeLock(){
        writeLock.lock();
    }
    public void readLock(){
        readLock.lock();
    }

    /**
     * 解锁
     */
    public void readUnlock(){
        readLock.unlock();
    }
    public void writeUnlock() {writeLock.unlock(); }

}
