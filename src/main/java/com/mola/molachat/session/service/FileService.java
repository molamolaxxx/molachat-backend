package com.mola.molachat.session.service;

import org.springframework.data.util.Pair;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * @Author: molamola
 * @Date: 19-9-12 上午10:39
 * @Version 1.0
 */
public interface FileService {

    /**
     * 存储文件
     * @param file
     * @return true, snapshot
     */
    Pair<String, String> save(MultipartFile file) throws IOException;

    /**
     * 上传文件完成的回调
     */
    void extend(String chatterId);
}
