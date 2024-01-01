package com.mola.molachat.common.handler;

import com.mola.molachat.common.annotation.Handler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

/**
 * @author : molamola
 * @Project: edu
 * @Description:
 * @date : 2020-11-12 16:35
 **/
@Handler
@Slf4j
public class FileTransferHandler {

    private static final String NOT_FOUND_IMAGE_LOCATION = "static/img/404.jpeg";

    private File notFoundFile;

    @PostConstruct
    public void post() throws IOException {
        ClassPathResource classPathResource = new ClassPathResource(NOT_FOUND_IMAGE_LOCATION);
        InputStream inputStream = classPathResource.getInputStream();
        //生成目标文件
        notFoundFile = File.createTempFile("404_snapshot", ".jpeg");
        try {
            FileUtils.copyInputStreamToFile(inputStream, notFoundFile);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }
    /**
     * 将本地文件传输到http输出流
     * @param path
     * @param response
     */
    public boolean transfer(String path, HttpServletResponse response){
        File file = new File(path);
        // 获得文件输入流
        InputStream fileInputStream = null;
        BufferedInputStream bufferedInputStream = null;
        BufferedOutputStream bufferedOutputStream = null;
        try {
            if (!file.exists()) {
                file = notFoundFile;
            }

            fileInputStream = new FileInputStream(file);
            // 装饰成bufferedStream
            bufferedInputStream = new BufferedInputStream(fileInputStream);
            if (com.mola.molachat.common.utils.FileUtils.isImage(path)) {
                response.setContentType("image/png");
            }
//            response.setHeader("Content-disposition", "attachment; filename=" + new String(file.getName().getBytes("utf-8"), "utf-8"));
            response.setHeader("Content-Length", String.valueOf(file.length()));

            bufferedOutputStream = new BufferedOutputStream(response.getOutputStream());
            byte[] buffer = new byte[1024];
            int read = 0;
            while (-1 != (read = bufferedInputStream.read(buffer, 0, buffer.length))){
                bufferedOutputStream.write(buffer, 0, read);
            }

        } catch (FileNotFoundException e) {
            log.error("文件未找到,"+ e.getMessage(), e);
            return false;
        } catch (IOException e) {
            log.error("出现io错误,"+ e.getMessage(), e);
            return false;
        } finally {
            try {
                if (null != bufferedInputStream) {
                    bufferedInputStream.close();
                }
                if (null != bufferedOutputStream) {
                    bufferedOutputStream.close();
                }
            } catch (IOException e) {
                log.error("关闭文件出错,"+ e.getMessage(), e);
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws IOException {
//        URL resource = FileTransferHandler.class.getClassLoader().getResource("static/img/404.jpeg");
//        File file = new File(resource.getFile());
//        System.out.println(file.exists());
        ClassPathResource resource = new ClassPathResource("static/img/404.jpeg");
        System.out.println(resource.getFile().exists());
    }
}
