package com.mola.molachat.common.exception;

import com.mola.molachat.common.enums.DataErrorCodeEnum;
import lombok.Data;

/**
 * @Author: molamola
 * @Date: 19-8-5 下午3:19
 * @Version 1.0
 * 数据模块异常
 */
@Data
public class ChatterException extends AppBaseException{

    Integer code ;

    public ChatterException(DataErrorCodeEnum result){
        super(result.getMsg());
        this.code = result.getCode();
    }

    public ChatterException(DataErrorCodeEnum result, String message){
        super(message);
        this.code = result.getCode();
    }
}
