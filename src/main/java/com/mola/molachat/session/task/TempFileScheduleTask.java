package com.mola.molachat.session.task;

import com.mola.molachat.common.config.SelfConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Resource;
import java.io.File;

/**
 * 定时清理超过3天的临时文件
 * 临时文件存储在 uploadFilePath/temp 目录下
 */
@Configuration
@EnableScheduling
@Slf4j
public class TempFileScheduleTask {

    private static final long THREE_DAYS_MS = 3L * 24 * 60 * 60 * 1000;

    @Resource
    private SelfConfig selfConfig;

    /**
     * 每小时检查一次，删除超过3天的临时文件
     */
    @Scheduled(initialDelay = 120000, fixedRate = 3600000)
    private void cleanExpiredTempFiles() {
        String tempDir = selfConfig.getUploadFilePath() + File.separator + "temp";
        File dir = new File(tempDir);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        long now = System.currentTimeMillis();
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        int deleted = 0;
        for (File f : files) {
            if (f.isFile() && (now - f.lastModified() > THREE_DAYS_MS)) {
                if (f.delete()) {
                    deleted++;
                }
            }
        }

        if (deleted > 0) {
            log.info("临时文件清理完成，删除了{}个过期文件", deleted);
        }
    }
}
