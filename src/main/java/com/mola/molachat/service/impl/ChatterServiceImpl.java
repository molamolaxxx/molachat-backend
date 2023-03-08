package com.mola.molachat.service.impl;

import com.mola.molachat.annotation.AddPoint;
import com.mola.molachat.annotation.RefreshChatterList;
import com.mola.molachat.config.SelfConfig;
import com.mola.molachat.data.ChatterFactoryInterface;
import com.mola.molachat.entity.Chatter;
import com.mola.molachat.entity.Message;
import com.mola.molachat.entity.RobotChatter;
import com.mola.molachat.entity.dto.ChatterDTO;
import com.mola.molachat.enumeration.ChatterPointEnum;
import com.mola.molachat.enumeration.ChatterStatusEnum;
import com.mola.molachat.enumeration.ServiceErrorEnum;
import com.mola.molachat.exception.ChatterException;
import com.mola.molachat.exception.service.ChatterServiceException;
import com.mola.molachat.service.ChatterService;
import com.mola.molachat.utils.BeanUtilsPlug;
import com.mola.molachat.utils.CopyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Author: molamola
 * @Date: 19-8-6 下午4:17
 * @Version 1.0
 */
@Service
@Slf4j
public class ChatterServiceImpl implements ChatterService {

    @Autowired
    private ChatterFactoryInterface chatterFactory;

    @Autowired
    private SelfConfig selfConfig;

    @Override
    public ChatterDTO create(ChatterDTO chatterDTO) throws ChatterServiceException {

        //1.chatterDTO内部应该包含 :ip, name,默认不为空
        Chatter chatter = new Chatter();
        BeanUtils.copyProperties(chatterDTO, chatter);
        List<Chatter> chatterList = chatterFactory.list();

        //2.检查名称是否重复
        List<String> nameList = chatterList.stream()
                .map(e -> e.getName())
                .collect(Collectors.toList());
        if (nameList.contains(chatterDTO.getName())){
            log.info("名称重复,名称:{}", chatterDTO.getName());
            throw new ChatterServiceException(ServiceErrorEnum.CHATTER_NAME_DUPLICATE);
        }
        //3.检查ip是否已经注册，默认一个客户端只能登录一个窗口
//        List<String> ipList = chatterList.stream()
//                .map(e -> e.getIp())
//                .collect(Collectors.toList());
//        if (ipList.contains(chatterDTO.getIp())){
//            log.info("ip已经登录,ip:{}",chatterDTO.getIp());
//            throw new ChatterServiceException(ServiceErrorEnum.CHATTER_IP_DUPLICATE);
//        }
        Chatter result = chatterFactory.create(chatter);

        return (ChatterDTO) BeanUtilsPlug.copyPropertiesReturnTarget(result, chatterDTO);
    }

    @Override
    @AddPoint(action = ChatterPointEnum.UPDATE, key = "#chatterDTO.id")
    public ChatterDTO update(ChatterDTO chatterDTO) throws ChatterServiceException{

        //1.根据chatterId查找chatter
        Chatter chatter = chatterFactory.select(chatterDTO.getId());
        if (null == chatter){
            //异常
            throw new ChatterServiceException(ServiceErrorEnum.CHATTER_NOT_FOUND);
        }
        //2.如果存在名称，检查名称是否重复
        if (null != chatterDTO.getName()) {
            List<String> nameList = chatterFactory.list().stream()
                    .map(e -> e.getName())
                    .collect(Collectors.toList());
            if (nameList.contains(chatterDTO.getName())) {
                log.info("名称重复");
                throw new ChatterServiceException(ServiceErrorEnum.CHATTER_NAME_DUPLICATE);
            }
        }
        chatterDTO.setPoint(chatter.getPoint());
        //copy非空值到chatter
        CopyUtils.copyProperties(chatterDTO, chatter);
        //存储
        try {
            chatterFactory.update(chatter);
        } catch (ChatterException e) {
            throw new ChatterServiceException(ServiceErrorEnum.UPDATE_CHATTER_ERROR);
        }
        return (ChatterDTO)BeanUtilsPlug.copyPropertiesReturnTarget(chatter, chatterDTO);
    }

    @Override
    @AddPoint(action = ChatterPointEnum.UPDATE, key = "#chatterDTO.id")
    public ChatterDTO updateRobot(ChatterDTO chatterDTO) {
        //1.根据chatterId查找chatter
        Chatter chatter = chatterFactory.select(chatterDTO.getId());
        Assert.notNull(chatter, ServiceErrorEnum.CHATTER_NOT_FOUND.getMsg());
        Assert.isTrue(chatter instanceof RobotChatter, "非机器人");
        RobotChatter robotChatter = (RobotChatter) chatter;
        //2.如果存在名称，检查名称是否重复
        if (null != robotChatter.getName()) {
            List<String> nameList = chatterFactory.list().stream()
                    .map(e -> e.getName())
                    .collect(Collectors.toList());
            if (nameList.contains(chatterDTO.getName())) {
                log.info("名称重复");
                throw new ChatterServiceException(ServiceErrorEnum.CHATTER_NAME_DUPLICATE);
            }
        }
        chatterDTO.setPoint(chatter.getPoint());
        //copy非空值到chatter
        CopyUtils.copyProperties(chatterDTO, robotChatter);
        //存储
        try {
            chatterFactory.update(robotChatter);
        } catch (ChatterException e) {
            throw new ChatterServiceException(ServiceErrorEnum.UPDATE_CHATTER_ERROR);
        }
        return (ChatterDTO)BeanUtilsPlug.copyPropertiesReturnTarget(robotChatter, chatterDTO);
    }

    @Override
    public ChatterDTO remove(ChatterDTO chatterDTO) throws ChatterServiceException{

        Chatter chatter = new Chatter();
        BeanUtils.copyProperties(chatterDTO, chatter);
        chatterFactory.remove(chatter);
        return chatterDTO;
    }

    @Override
    public List<ChatterDTO> list() {
        List<Chatter> chatterList = chatterFactory.list();
        List<ChatterDTO> chatterDTOList = chatterList.stream().map(e -> {
            ChatterDTO dto =  (ChatterDTO)BeanUtilsPlug.copyPropertiesReturnTarget(e, new ChatterDTO());
//            dto.setVideoState(e.getVideoState());
            if (e instanceof RobotChatter) {
                dto.setRobot(true);
            }
            return dto;
        }).collect(Collectors.toList());

        return chatterDTOList;
    }

    @Override
    public ChatterDTO selectById(String chatterId) {

        Chatter chatter = chatterFactory.select(chatterId);
        if (null == chatter)
            return null;
        ChatterDTO result = (ChatterDTO) BeanUtilsPlug.copyPropertiesReturnTarget(chatter, new ChatterDTO());
//        result.setVideoState(chatter.getVideoState());
        if (chatter instanceof RobotChatter) {
            result.setRobot(true);
        }
        return result;
    }

    @Override
    @RefreshChatterList
    public void setChatterStatus(String chatterId, Integer status) {
        Chatter chatter = chatterFactory.select(chatterId);
        if (null == chatter){
            throw new ChatterServiceException(ServiceErrorEnum.CHATTER_NOT_FOUND);
        }
        chatter.setStatus(status);

        chatterFactory.update(chatter);
    }

    @Override
    @RefreshChatterList
    public void setChatterTag(String chatterId, Integer tag) {
        Chatter chatter = chatterFactory.select(chatterId);
        if (null == chatter){
            throw new ChatterServiceException(ServiceErrorEnum.CHATTER_NOT_FOUND);
        }
        chatter.setTag(tag);
        chatterFactory.update(chatter);
    }

    @Override
    public ChatterDTO save(ChatterDTO chatterDTO) {
        chatterFactory.save((Chatter)BeanUtilsPlug.copyPropertiesReturnTarget(chatterDTO, new Chatter()));
        return chatterDTO;
    }

    @Override
    public BlockingQueue<Message> getQueueById(String chatterId) {
        return chatterFactory.queue(chatterId);
    }

    @Override
    public void offerMessageIntoQueue(Message message, String chatterId) {
        BlockingQueue<Message> queue = this.getQueueById(chatterId);
        if (null != queue) {
            queue.offer(message);
            // 如果队列大于最大值，弹出多余元素
            while (queue.size() > selfConfig.getIntMAX_MESSAGE_QUEUE_NUM()) {
                try {
                    queue.poll(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void addPoint(String id, Integer point) {
        Chatter chatter = chatterFactory.select(id);
        // 针对每一个chatter进行锁，降低锁粒度
        synchronized (chatter) {
            if (null != chatter) {
                Integer rs = chatter.getPoint() + point;
                if (rs > 10000) {
                    rs = 10000;
                }
                if (rs < 0) {
                    rs = 0;
                }
                chatter.setPoint(rs);
            }
        }
    }

    @Override
    public boolean casVideoState(String chatterId, Integer pre, Integer cur) {
        Chatter toChange = chatterFactory.select(chatterId);
        return toChange.getVideoState().compareAndSet(pre, cur);
    }

    @Override
    public void changeVideoState(String chatterId, Integer state) {
        Chatter toChange = chatterFactory.select(chatterId);
        toChange.getVideoState().set(state);
    }

    @Override
    public boolean isOnlineChatterOverflow() {
        // 筛选出在线的人数
        int onlineChatterSize = chatterFactory.list().stream().filter(
                e -> ChatterStatusEnum.ONLINE.getCode() == e.getStatus()
        ).collect(Collectors.toList()).size();
        return onlineChatterSize >= selfConfig.getMAX_CLIENT_NUM();
    }
}
