package com.mola.molachat.exception.service;

import com.mola.molachat.enumeration.ServiceErrorEnum;
import com.mola.molachat.exception.AppBaseException;
import lombok.Data;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-05-21 17:44
 **/
@Data
public class GroupServiceException extends AppBaseException {
    Integer code ;

    public GroupServiceException(ServiceErrorEnum result){
        super(result.getMsg());
        this.code = result.getCode();
    }

    public GroupServiceException(ServiceErrorEnum result, String message){
        super(message);
        this.code = result.getCode();
    }

}
