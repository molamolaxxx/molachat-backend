package com.mola.molachat.annotation;

import com.mola.molachat.enumeration.ChatterPointEnum;

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
