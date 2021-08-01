package com.mola.molachat.schedule;

/**
 * @Author: molamola
 * @Date: 19-8-18 下午11:34
 * @Version 1.0
 */
public class ServerScheduleTaskTest {
    public static void main(String[] args){
        while (true){
            System.out.println(System.currentTimeMillis());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}