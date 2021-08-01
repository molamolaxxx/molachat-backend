package com.mola.molachat.controller;

import com.mola.molachat.config.SelfConfig;
import com.mola.molachat.data.SessionFactoryInterface;
import com.mola.molachat.entity.FileMessage;
import com.mola.molachat.entity.Message;
import com.mola.molachat.entity.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-05-28 21:43
 **/
@RestController
@RequestMapping("/change")
@Slf4j
public class TestController {

    @Resource
    private SessionFactoryInterface sessionFactoryInterface;

    @Resource
    private SelfConfig config;

    @GetMapping("/snapshot")
    public void scanner() {
        // 遍历所有消息
        List<Session> list = sessionFactoryInterface.list();
        for (Session session : list) {
            for(Message message : session.getMessageList()) {
                if (message instanceof FileMessage) {
                    FileMessage fm = (FileMessage) message;
//                    if (null == fm.getSnapshotUrl()) {
//                        String url = fm.getUrl();
//                        if (!(url.toLowerCase().endsWith(".jpg") || url.toLowerCase().endsWith(".png") ||
//                                url.toLowerCase().endsWith(".jpeg"))) {
//                            continue;
//                        }
//                        String from = config.getUploadFilePath() + File.separator + url.substring(6);
//                        String to = config.getUploadFilePath() + File.separator + "snapshot_" + url.substring(6);
//                        // 1、压缩
//                        try {
//                            log.info("compress start ! messageId = {}", message.getId());
//                            FileUtils.imageFileCompress(from, to, Long.parseLong(fm.getFileStorage()));
//                            log.info("compress done ! messageId = {}", message.getId());
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//                        // 2、赋值
//                        fm.setSnapshotUrl("/file/snapshot_" + url.substring(6));
//                    }
//                    if (fm.getSnapshotUrl().startsWith("/")) {
//                        fm.setSnapshotUrl(fm.getSnapshotUrl().substring(1));
//                    }
                    if (null!= fm.getSnapshotUrl() && fm.getSnapshotUrl().contains("file/")) {
                        fm.setSnapshotUrl(fm.getSnapshotUrl().replace("file/","files/"));
                    }
                    if (null!= fm.getUrl() && fm.getUrl().contains("file/")) {
                        fm.setUrl(fm.getUrl().replace("file/","files/"));
                    }
                    sessionFactoryInterface.save(session.getSessionId());
                }
            }
        }
    }
}
