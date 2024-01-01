package com.mola.molachat.common.annotation;

import com.mola.molachat.chatter.enums.ChatterPointEnum;

import java.lang.annotation.*;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-05-01 12:49
 * 添加分数
 **/
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AddPoint {
    ChatterPointEnum action();

    String key() default "";
}
