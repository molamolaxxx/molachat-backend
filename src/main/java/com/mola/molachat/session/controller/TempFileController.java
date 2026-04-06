package com.mola.molachat.session.controller;

import com.mola.molachat.common.config.SelfConfig;
import com.mola.molachat.common.handler.FileTransferHandler;
import com.mola.molachat.common.model.ServerResponse;
import com.mola.molachat.common.utils.FileUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * 临时文件上传接口，通过base64接收文件
 * 文件存储在 uploadFilePath/temp 目录下，不会被已有的文件扫描逻辑删除
 * 最大保留3天，由 TempFileScheduleTask 定时清理
 */
@RestController
@RequestMapping("/temp-files")
@Slf4j
public class TempFileController {

    @Resource
    private SelfConfig selfConfig;

    @Resource
    private FileTransferHandler fileTransferHandler;

    @Data
    public static class Base64UploadRequest {
        /** base64编码的文件内容（不含data:xxx;base64,前缀） */
        private String base64Data;
        /** 文件名（含后缀） */
        private String fileName;
    }

    @PostMapping("/upload")
    public ServerResponse<String> upload(@RequestBody Base64UploadRequest request) {
        if (StringUtils.isBlank(request.getBase64Data()) || StringUtils.isBlank(request.getFileName())) {
            return ServerResponse.createByErrorMessage("base64Data和fileName不能为空");
        }

        try {
            // 去除可能携带的data uri前缀
            String base64 = request.getBase64Data();
            if (base64.contains(",")) {
                base64 = base64.substring(base64.indexOf(",") + 1);
            }

            byte[] fileBytes = Base64.getDecoder().decode(base64);

            // 构建临时文件目录
            String tempDir = selfConfig.getUploadFilePath() + File.separator + "temp";
            FileUtils.createDirSmart(tempDir);

            // 生成唯一文件名
            String storedName = RandomStringUtils.randomAlphabetic(8) + "_" + request.getFileName();
            String filePath = tempDir + File.separator + storedName;

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(fileBytes);
            }

            log.info("临时文件上传成功: {}", filePath);
            String accessUrl = "temp-files/" + storedName;
            return ServerResponse.createBySuccess(accessUrl);

        } catch (IllegalArgumentException e) {
            log.error("base64解码失败", e);
            return ServerResponse.createByErrorMessage("base64数据格式错误");
        } catch (IOException e) {
            log.error("临时文件写入失败", e);
            return ServerResponse.createByErrorMessage("文件保存失败: " + e.getMessage());
        }
    }

    @GetMapping("/{fileName}")
    public void download(@PathVariable String fileName, HttpServletResponse response) {
        String filePath = selfConfig.getUploadFilePath() + File.separator + "temp" + File.separator + fileName;
        fileTransferHandler.transfer(filePath, response);
    }
}
