package com.mola.molachat.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-04-02 14:12
 **/
@Configuration
public class ScheduledConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduling = new ThreadPoolTaskScheduler();
        scheduling.setPoolSize(4);
        scheduling.setThreadFactory(new ThreadFactory() {
            private AtomicInteger idx = new AtomicInteger();
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "molachat-schedler-"+idx.incrementAndGet());
                return t;
            }
        });
        scheduling.initialize();
        return scheduling;
    }
}
