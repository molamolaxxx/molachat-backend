package com.mola.molachat.data.impl.cache;

import com.mola.molachat.annotation.RefreshChatterList;
import com.mola.molachat.condition.CacheCondition;
import com.mola.molachat.config.SelfConfig;
import com.mola.molachat.data.ChatterFactoryInterface;
import com.mola.molachat.entity.Chatter;
import com.mola.molachat.entity.Message;
import com.mola.molachat.enumeration.DataErrorCodeEnum;
import com.mola.molachat.exception.ChatterException;
import com.mola.molachat.utils.IdUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @Author: molamola
 * @Date: 19-8-5 下午3:09
 * @Version 1.0
 * 管理chatter的工厂
 */
@Component
@Conditional(CacheCondition.class)
@Slf4j
public class ChatterFactory implements ChatterFactoryInterface {

    @Resource
    private SelfConfig config;

    /**
     * chatter数据保存
     */
    private static Map<String, Chatter> chatterData;

    /**
     * chatter消息队列，用于存放session中因为断线未发出的消息
     * @key: chatterId
     */
    private static Map<String, LinkedBlockingQueue<Message>> chatterMessageQueue;

    /**
     * 初始化
     */
    public ChatterFactory(){
        chatterData = new ConcurrentHashMap<>();
        chatterMessageQueue = new ConcurrentHashMap<>();
    }

    @Override
    public Chatter create(Chatter chatter){
        //赋值id
        if (chatter.getId() == null) {
            chatter.setId(IdUtils.getChatterId());
        }

        //判断是否溢出
        if (isOverFlow()){
            throw new ChatterException(DataErrorCodeEnum.CHATTER_OVER_FLOW);
        }
        chatter.setCreateTime(new Date());
        chatterData.put(chatter.getId(), chatter);
        chatterMessageQueue.put(chatter.getId(), new LinkedBlockingQueue<>());
        return chatter;
    }

    @Override
    @RefreshChatterList
    public Chatter update(Chatter chatter){
        chatterData.replace(chatter.getId(), chatter);
        return chatter;
    }

    @Override
    @RefreshChatterList
    public Chatter remove(Chatter chatter){
        chatterData.remove(chatter.getId());
        chatterMessageQueue.remove(chatter.getId());
        return chatter;
    }

    @Override
    public Chatter select(String id) {
        return chatterData.get(id);
    }

    @Override
    public List<Chatter> list() {
        List<Chatter> resultList = new ArrayList<>();

        for (String key : chatterData.keySet()){
            resultList.add(chatterData.get(key));
        }
        return resultList;
    }

    private Boolean isOverFlow(){
        if (chatterData.size() < config.getMAX_CLIENT_NUM()){
            return false;
        }
        return true;
    }

    @Override
    public Chatter save(Chatter chatter) {
        chatterData.put(chatter.getId(), chatter);
        return chatter;
    }

    @Override
    public BlockingQueue<Message> queue(String id) {
        BlockingQueue<Message> queue =  chatterMessageQueue.get(id);
        if (null == queue) {
            chatterMessageQueue.put(id, new LinkedBlockingQueue<>());
        }
        return chatterMessageQueue.get(id);
    }


    /**
     * setters : 因为后续有动态加载bean，需要loadBeanDefination，采用setter的方式进行属性注入
     * 对于cache包下每一个依赖的bean，都必须使用setter
     */
    public void setSelfConfig(SelfConfig config) {
        this.config = config;
    }
}
