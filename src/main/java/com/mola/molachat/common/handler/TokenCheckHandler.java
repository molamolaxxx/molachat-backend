package com.mola.molachat.common.handler;

import com.mola.molachat.common.utils.IpUtils;
import com.mola.molachat.common.utils.JwtTokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 检查token
 * @date : 2021-10-04 22:08
 **/
@Component
@Slf4j
public class TokenCheckHandler {

    @Resource
    private JwtTokenUtil jwtUtil;

    /**
     * 检查token是否有效
     * @param id
     * @param token
     * @param request
     * @return
     */
    public Boolean checkToken(String id, String token, HttpServletRequest request){
        boolean rs = jwtUtil.validateToken(token, id);
        if (!rs) {
            log.info("[checkToken]token验证错误，id = {},token = {},ip = {}", id, token, IpUtils.getIp(request));
        }
        return rs;
    }


}
