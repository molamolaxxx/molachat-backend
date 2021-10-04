package com.mola.molachat.data.impl;

import com.mola.molachat.data.impl.cache.ChatterFactory;
import com.mola.molachat.data.impl.cache.SessionFactory;
import com.mola.molachat.entity.Chatter;
import com.mola.molachat.entity.Message;
import com.mola.molachat.entity.Session;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashSet;
import java.util.Set;

/**
 * @Author: molamola
 * @Date: 19-8-5 下午10:14
 * @Version 1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
@Slf4j
public class SessionFactoryTest {

    @Autowired
    private SessionFactory sessionFactory;

    @Autowired
    private ChatterFactory chatterFactory;
    @Test
    public void create() {

        Set<Chatter> set = new HashSet();

        set.add(chatterFactory.create(new Chatter()));
        set.add(chatterFactory.create(new Chatter()));

        Session session = sessionFactory.create(set);
        log.info(session.toString());

        for (int i = 0 ; i < 100 ; i++ )
            sessionFactory.insertMessage(session.getSessionId(),new Message());

        log.info("finish");
    }
    @Test
    public void selectById() {
    }

    @Test
    public void remove() {
    }

    @Test
    public void insertMessage() {
    }
}