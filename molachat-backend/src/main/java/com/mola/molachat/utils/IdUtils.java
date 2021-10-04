package com.mola.molachat.utils;

import java.util.Random;

/**
 * @Author: molamola
 * @Date: 19-8-5 下午5:11
 * @Version 1.0
 */
public class IdUtils {

    /**
     * 聊天者id 18位
     * @return
     */
    public synchronized static String getChatterId(){
        return System.currentTimeMillis() + getRandomString(5);
    }

    /**
     * sessionId 22位
     * @return
     */
    public synchronized static String getSessionId(){
        return System.currentTimeMillis() + getRandomString(9);
    }

    /**
     * messageId 25位
     * @return
     */
    public synchronized static String getMessageId(){
        return System.currentTimeMillis() + getRandomString(12);
    }

    /**
     * groupId 23位
     * @return
     */
    public synchronized static String getGroupId() {
        return System.currentTimeMillis() + getRandomString(10);
    }

    public static String getRandomString(int length){

        String str="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random=new Random();
        StringBuffer sb=new StringBuffer();
        for(int i=0;i<length;i++){
            int number=random.nextInt(62);
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }

}
