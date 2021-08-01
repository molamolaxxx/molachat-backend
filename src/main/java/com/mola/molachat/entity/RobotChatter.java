package com.mola.molachat.entity;

import lombok.Data;

import java.util.UUID;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-12-05 19:36
 **/
@Data
public class RobotChatter extends Chatter {
    /**
     * 唯一appkey，用于外部api调用
     */
    private String appKey = UUID.randomUUID().toString();

//    /**
//     * 入站插槽
//     */
//    private Set<InboundSlot> inboundSlotList = new HashSet<>();
//
//    public void addInboundSlot(InboundSlot inboundSlot) {
//        if (null != inboundSlot) {
//            inboundSlotList.add(inboundSlot);
//        }
//    }
}
