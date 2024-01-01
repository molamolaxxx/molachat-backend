package com.mola.molachat.common.exceptionhandler;

import com.mola.molachat.common.model.ServerResponse;
import com.mola.molachat.common.exception.AppBaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-05-21 18:11
 **/
@ControllerAdvice
@Slf4j
public class CommonExceptionHandler {

    @ExceptionHandler(AppBaseException.class)
    @ResponseBody
    private ServerResponse exception(AppBaseException appBaseException){
        log.error(appBaseException.getMessage());
        return ServerResponse.createByErrorMessage(appBaseException.getMessage());
    }

}
