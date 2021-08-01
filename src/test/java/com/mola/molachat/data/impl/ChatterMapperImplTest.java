package com.mola.molachat.data.impl;

import com.mola.molachat.data.impl.cache.ChatterFactory;
import com.mola.molachat.entity.Chatter;
import com.mola.molachat.exception.ChatterException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

/**
 * @Author: molamola
 * @Date: 19-8-5 下午3:53
 * @Version 1.0
 */
@SpringBootTest
@RunWith(SpringRunner.class)
@Slf4j
public class ChatterMapperImplTest {

    @Autowired
    private ChatterFactory mapper;

    @Test
    public void add() {
        for (int i = 0 ; i < 30 ; i++ ) {
            Chatter chatter = new Chatter();
            chatter.setIp("192.168.1.120");
            chatter.setName("翻车猫");
            try {
                mapper.create(chatter);
            } catch (ChatterException e) {
                log.error(e.toString());
            }
        }

        List chatterList =  mapper.list();

        log.info("finish");
    }

    @Test
    public void update() {
    }

    @Test
    public void remove() {
    }

    @Test
    public void select() {
    }

    @Test
    public void list() {
    }
}