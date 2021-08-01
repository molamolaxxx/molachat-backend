package com.mola.molachat.exception;

import com.mola.molachat.enumeration.DataErrorCodeEnum;
import lombok.Data;

/**
 * @Author: molamola
 * @Date: 19-8-6 上午11:30
 * @Version 1.0
 */
@Data
public class ServerException extends AppBaseException{

    Integer code ;

    public ServerException(DataErrorCodeEnum result){
        super(result.getMsg());
        this.code = result.getCode();
    }

    public ServerException(DataErrorCodeEnum result, String message){
        super(message);
        this.code = result.getCode();
    }
}
