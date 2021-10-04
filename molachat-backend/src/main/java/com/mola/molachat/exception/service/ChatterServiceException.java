package com.mola.molachat.exception.service;

import com.mola.molachat.enumeration.ServiceErrorEnum;
import com.mola.molachat.exception.AppBaseException;
import lombok.Data;

/**
 * @Author: molamola
 * @Date: 19-8-5 下午3:19
 * @Version 1.0
 * 数据模块异常
 */
@Data
public class ChatterServiceException extends AppBaseException {

    Integer code ;

    public ChatterServiceException(ServiceErrorEnum result){
        super(result.getMsg());
        this.code = result.getCode();
    }

    public ChatterServiceException(ServiceErrorEnum result, String message){
        super(message);
        this.code = result.getCode();
    }
}
