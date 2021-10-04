package com.mola.molachat.exception.service;

import com.mola.molachat.enumeration.ServiceErrorEnum;
import com.mola.molachat.exception.AppBaseException;
import lombok.Data;

/**
 * @Author: molamola
 * @Date: 19-8-6 上午11:30
 * @Version 1.0
 */
@Data
public class ServerServiceException extends AppBaseException {

    Integer code ;

    public ServerServiceException(ServiceErrorEnum result){
        super(result.getMsg());
        this.code = result.getCode();
    }

    public ServerServiceException(ServiceErrorEnum result, String message){
        super(message);
        this.code = result.getCode();
    }
}
