package com.mola.molachat.common.exception.service;

import com.mola.molachat.common.enums.ServiceErrorEnum;
import com.mola.molachat.common.exception.AppBaseException;
import lombok.Data;


/**
 * @Author: molamola
 * @Date: 19-8-5 下午9:08
 * @Version 1.0
 */
@Data
public class SessionServiceException extends AppBaseException {

    Integer code ;

    public SessionServiceException(ServiceErrorEnum result){
        super(result.getMsg());
        this.code = result.getCode();
    }

    public SessionServiceException(ServiceErrorEnum result, String message){
        super(message);
        this.code = result.getCode();
    }
}
