package com.mola.molachat.robot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.StringUtils;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-09-23 19:05
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyValue {

    /**
     * 键
     */
    private String key;

    /**
     * 值
     */
    private String value;

    /**
     * 管理员
     */
    private String owner;

    /**
     * 描述
     */
    private String desc;

    /**
     * 是否共享
     */
    private boolean share;

    @Override
    public String toString() {
        String showValue = value;
        if (value.length() > 16) {
            showValue = StringUtils.substring(showValue, 0, 16) + "...";
        }
        return "【" + key + "】" +
                ": value=" + showValue +
                ", owner=" + owner +
                ", share=" + share +
                ", desc=" + desc;
    }
}
