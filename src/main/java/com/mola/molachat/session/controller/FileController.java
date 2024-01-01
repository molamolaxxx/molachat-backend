package com.mola.molachat.session.controller;

import com.mola.molachat.common.model.ResponseCode;
import com.mola.molachat.common.model.ServerResponse;
import com.mola.molachat.common.lock.FileUploadLock;
import com.mola.molachat.common.config.SelfConfig;
import com.mola.molachat.session.model.FileMessage;
import com.mola.molachat.common.handler.FileTransferHandler;
import com.mola.molachat.session.service.FileService;
import com.mola.molachat.session.solution.SessionSolution;
import com.mola.molachat.common.utils.JwtTokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload.FileUploadBase;
import org.springframework.data.util.Pair;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;

/**
 * @Author: molamola
 * @Date: 19-9-12 上午10:04
 * @Version 1.0
 */
@RestController
@RequestMapping("/files")
@Slf4j
public class FileController {

    @Resource
    private FileService fileService;

    @Resource
    private SessionSolution sessionSolution;

    @Resource
    private FileUploadLock lock;

    @Resource
    private FileTransferHandler fileTransferHandler;

    @Resource
    private SelfConfig selfConfig;

    @Resource
    private JwtTokenUtil jwtUtil;

    @PostMapping("/upload")
    @ExceptionHandler(value = FileUploadBase.SizeLimitExceededException.class)
    private ServerResponse upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam("chatterId") String chatterId,
                                  @RequestParam("token") String token,
                                  @RequestParam("sessionId") String sessionId,
                                  HttpServletRequest request, HttpServletResponse response){
        //token验证
        if (!checkToken(chatterId, token)){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ServerResponse.createByErrorCodeMessage(ResponseCode.ERROR.getCode(),
                    "token验证错误");
        }
        //存储文件
        Pair<String, String> pathPair =  null;
        try {
            //上锁
            lock.writeLock();
            // 保存真实文件和快照
            pathPair = fileService.save(file);
            fileService.extend(chatterId);
            //创建message
            FileMessage fileMessage = new FileMessage();
            fileMessage.setFileName(file.getOriginalFilename());
            fileMessage.setFileStorage(String.valueOf(file.getSize()));
            fileMessage.setUrl(pathPair.getFirst());
            fileMessage.setSnapshotUrl(pathPair.getSecond());
            fileMessage.setChatterId(chatterId);

            // 判断是否是群聊
            if (sessionId.equals("common-session")) {
                fileMessage.setCommon(true);
            }
            sessionSolution.insertMessage(sessionId, fileMessage);

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return ServerResponse.createByErrorMessage(e.getMessage());
        } finally {
            //解锁
            lock.writeUnlock();
        }

        return ServerResponse.createBySuccess(pathPair.getFirst());
    }

    @GetMapping("/{fileName}")
    public void find(@PathVariable String fileName, HttpServletResponse response) {
        String fPath = selfConfig.getUploadFilePath() + File.separator + fileName;
        fileTransferHandler.transfer(fPath, response);
    }

    private Boolean checkToken(String id, String token){
        return jwtUtil.validateToken(token, id);
    }
}
