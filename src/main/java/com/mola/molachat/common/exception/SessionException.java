package com.mola.molachat.common.exception;

import com.mola.molachat.common.enums.DataErrorCodeEnum;
import lombok.Data;

/**
 * @Author: molamola
 * @Date: 19-8-5 下午9:08
 * @Version 1.0
 */
@Data
public class SessionException extends AppBaseException{

    Integer code ;

    public SessionException(DataErrorCodeEnum result){
        super(result.getMsg());
        this.code = result.getCode();
    }

    public SessionException(DataErrorCodeEnum result, String message){
        super(message);
        this.code = result.getCode();
    }
}
