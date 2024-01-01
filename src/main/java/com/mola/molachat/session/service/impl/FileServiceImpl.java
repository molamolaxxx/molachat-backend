package com.mola.molachat.session.service.impl;

import com.mola.molachat.common.annotation.AddPoint;
import com.mola.molachat.common.config.SelfConfig;
import com.mola.molachat.chatter.enums.ChatterPointEnum;
import com.mola.molachat.session.service.FileService;
import com.mola.molachat.common.utils.FileUtils;
import org.springframework.data.util.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;

/**
 * @Author: molamola
 * @Date: 19-9-12 上午10:39
 * @Version 1.0
 */
@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Resource
    private SelfConfig config;

    @Override
    public Pair<String, String> save(MultipartFile file) throws IOException{
        String fileName = RandomStringUtils.randomAlphabetic(5) + "_" + file.getOriginalFilename();
        String folderPath = config.getUploadFilePath();
        if (!StringUtils.isNotBlank(folderPath)) {
            throw new IllegalStateException("上传文件夹目录为空，不能创建");
        }
        // 创建多级文件夹目录
        FileUtils.createDirSmart(folderPath);
        String url = folderPath + File.separator + fileName;
        String snapshotPath = folderPath + File.separator + "snapshot_" + fileName;
        log.info(url);
        File localTmpFile = new File(url);
        //写入文件，此处不能直接getByte，否则会导致内存溢出
        file.transferTo(localTmpFile);
        // 如果是图片，进行快照转储
        if (FileUtils.isImage(fileName) && file.getSize() >= 250000L) {
            FileUtils.imageFileCompress(url, snapshotPath, file.getSize());
            return Pair.of("files/"+fileName, "files/snapshot_"+fileName);
        }
        return Pair.of("files/"+fileName, "files/"+fileName);
    }

    @Override
    @AddPoint(action = ChatterPointEnum.SEND_FILE, key = "#chatterId")
    public void extend(String chatterId) {
    }
}
