package com.mola.molachat.common.annotation;

import java.lang.annotation.*;

/**
 * @Author: molamola
 * @Date: 19-7-31 上午9:25
 * @Version 1.0
 * 刷新chatterList
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RefreshChatterList {
}
