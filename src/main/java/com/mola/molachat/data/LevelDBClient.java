package com.mola.molachat.data;

import com.mola.molachat.config.AppConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: leveDB 操作类
 * @date : 2023-03-02 17:58
 **/
@Slf4j
public class LevelDBClient {

    @Resource
    private AppConfig appConfig;

    private DB db;

    public void init() {
        String path = appConfig.getLevelDBStoragePrefix() + "_" + appConfig.getId();
        log.info("初始化levelDB, db_path = " + path);
        // Create a new database instance
        Options options = new Options();
        options.createIfMissing(true);
        try {
            this.db = Iq80DBFactory.factory.open(new File(path), options);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String get(String key) {
        byte[] bytes = db.get(key.getBytes(StandardCharsets.UTF_8));
        if (bytes == null) {
            return null;
        }
        return new String(bytes);
    }

    public void put(String key, String value) {
        if (key == null || value == null) {
            return;
        }
        db.put(key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8));
    }

    public void delete(String key) {
        if (key == null) {
            return;
        }
        db.delete(key.getBytes(StandardCharsets.UTF_8));
    }

    public Map<String, String> list(String prefix) {
        Map<String, String> result = new HashMap<>();
        DBIterator iterator = db.iterator();
        while (iterator.hasNext()) {
            Map.Entry<byte[], byte[]> entry = iterator.next();
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = new String(entry.getKey());
            String value = new String(entry.getValue());
            if (StringUtils.startsWith(key, prefix)) {
                result.put(key, value);
            }
        }
        return result;
    }

    @PreDestroy
    public void destroy() {
        if (db != null)  {
            try {
                db.close();
            } catch (IOException e) {
                log.error("levelDB, close error accur", e);
            }
        }
    }

}
