package com.mola.molachat.data;

import java.util.Set;
import java.util.function.Consumer;

public interface OtherDataInterface {

    /**
     * 获取、存储gpt3的子序列
     * @return
     */
    Set<String> getGpt3ChildTokens();

    /**
     * 对set执行操作
     * @param operate
     */
    void operateGpt3ChildTokens(Consumer<Set<String>> operate);
}
