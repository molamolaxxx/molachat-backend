package com.mola.molachat.common.utils;

import com.mola.molachat.chatter.model.Chatter;

import java.util.Set;

/**
 * @Author: molamola
 * @Date: 19-8-5 下午5:45
 * @Version 1.0
 * 数据工具包
 */
public class DataUtils {

    /**
     * 判断集合是否相等
     * @param set1
     * @param set2
     * @return
     */
    public static Boolean equals(Set<Chatter> set1, Set<Chatter> set2){

        if (set1.size() != set2.size())
            return false;

        if (set1.containsAll(set2)){
            return true;
        }
        else {
            return false;
        }
    }
}
