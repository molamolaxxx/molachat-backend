package com.mola.molachat.entity;

import lombok.Data;

/**
 * @Author: molamola
 * @Date: 19-9-11 下午5:21
 * @Version 1.0
 * 文件聊天信息
 */
@Data
public class FileMessage extends Message{

    /**
     * 文件的访问地址
     */
    private String url;

    /**
     * 文件快照的访问地址，往往用来预览
     */
    private String snapshotUrl;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件大小
     */
    private String fileStorage;

    /**
     * 是否是群聊消息
     */
    private boolean isCommon = false;
}
