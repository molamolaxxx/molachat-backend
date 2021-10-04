package com.mola.molachat.common.websocket;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author: molamola
 * @Date: 19-8-9 上午11:19
 * @Version 1.0
 */
@Slf4j
public class WSResponseTest {

    @Test
    public void test(){
        Map<String, String> m = new HashMap();
        m.put("1","dsa");
        m.remove("2");
        log.info(m.toString());
    }
}