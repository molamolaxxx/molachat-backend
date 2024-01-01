package com.mola.molachat.session.task;

import com.mola.molachat.common.config.SelfConfig;
import com.mola.molachat.common.lock.FileUploadLock;
import com.mola.molachat.session.model.FileMessage;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.session.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Resource;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-04-30 10:22
 **/
@Configuration
@EnableScheduling
@Slf4j
public class FileScheduleTask {

    @Resource
    private FileUploadLock lock;

    @Resource
    private SessionService sessionService;

    @Resource
    private SelfConfig config;

    /**
     * 检查文件是否被消息持有
     */
    @Scheduled(initialDelay = 60000,fixedRate = 120000)
    private void cleanUselessCacheFile(){
        log.info("check:开始检查服务器文件有效性");

        Set<String> fileNameSet = new HashSet<>();
        //1.调出所有session的filemessage对象
        List<SessionDTO> sessionList = sessionService.list();
        for (SessionDTO sess : sessionList){
            for (Message message : sess.getMessageList()){
                if (message instanceof FileMessage){
                    //2.将文件名存入HashSet
                    String url = ((FileMessage) message).getUrl();
                    String fileName = url.substring(url.lastIndexOf('/') + 1);
                    fileNameSet.add(fileName);
                }
            }
        }
        File file = null;
        try {
            lock.writeLock();
            //3.判断是否存在,过滤存在文件
            file = new File(config.getUploadFilePath());
            //如果不存在文件夹，则创建
            if (!file.exists()){
                file.mkdir();
                log.info("创建文件夹成功");
            }
        } finally {
            lock.writeUnlock();
        }

        try {
            lock.readLock();
            if(file.isDirectory()){
                for (File f : file.listFiles(pathname -> { // ture 需要删除
                    String name = pathname.getName();
                    if (name.startsWith("snapshot_")) {
                        name = name.substring(9);
                    }
                    return !fileNameSet.contains(name);
                })){
                    f.delete();
                }
            }else {
                log.error("配置路径可能存在错误");
            }
        } finally {
            lock.readUnlock();
        }
    }
}
