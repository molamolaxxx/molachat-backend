package com.mola.molachat.controller;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.common.ResponseCode;
import com.mola.molachat.common.ServerResponse;
import com.mola.molachat.entity.Chatter;
import com.mola.molachat.entity.dto.ChatterDTO;
import com.mola.molachat.entity.dto.SessionDTO;
import com.mola.molachat.enumeration.ChatterStatusEnum;
import com.mola.molachat.enumeration.ChatterTagEnum;
import com.mola.molachat.enumeration.VideoStateEnum;
import com.mola.molachat.exception.service.ChatterServiceException;
import com.mola.molachat.form.ChatterForm;
import com.mola.molachat.handler.common.TokenCheckHandler;
import com.mola.molachat.server.ChatServer;
import com.mola.molachat.service.ChatterService;
import com.mola.molachat.service.ServerService;
import com.mola.molachat.service.SessionService;
import com.mola.molachat.utils.BeanUtilsPlug;
import com.mola.molachat.utils.IpUtils;
import com.mola.molachat.utils.JwtTokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.websocket.EncodeException;
import java.io.IOException;
import java.util.*;

/**
 * @Author: molamola
 * @Date: 19-8-7 下午9:12
 * @Version 1.0
 * 创建chatter
 */
@RestController
@RequestMapping("/chatter")
@Slf4j
public class ChatterController {

    @Resource
    private ChatterService chatterService;

    @Resource
    private ServerService serverService;

    @Resource
    private SessionService sessionService;

    @Resource
    private TokenCheckHandler tokenCheckHandler;

    @Resource
    private JwtTokenUtil jwtUtil;

    @PostMapping
    private ServerResponse create(
                                  @RequestParam("chatterName") String chatterName,
                                  @RequestParam("signature") String signature,
                                  @RequestParam("imgUrl") String imgUrl,
                                  @RequestParam(value = "preId", required = false) String preId, // 传入preID来，让一个客户端永远只对应一个id，防止创建失败
                                  HttpServletRequest request, HttpServletResponse response){
        ChatterDTO chatterDTO = new ChatterDTO();
        chatterDTO.setName(chatterName);
        chatterDTO.setIp(IpUtils.getIp(request));
        chatterDTO.setSignature(signature);
        chatterDTO.setImgUrl(imgUrl);
        if (StringUtils.isNotBlank(preId)) {
            chatterDTO.setId(preId);
        }
        chatterDTO.setStatus(ChatterStatusEnum.ONLINE.getCode());
        chatterDTO.setTag(ChatterTagEnum.VISITOR.getCode());
        ChatterDTO result = null;

        try {
            result = chatterService.create(chatterDTO);

        } catch (ChatterServiceException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return ServerResponse.createByErrorCodeMessage(e.getCode(), e.getMessage());
        }

        Map<String, String> resultMap = new HashMap();
        resultMap.put("id", result.getId());
        //设置jwt
        resultMap.put("token", jwtUtil.generateToken(result.getId()));
        return ServerResponse.createBySuccess(resultMap);
    }

    @PutMapping
    private ServerResponse update(@Valid ChatterForm form, BindingResult bindingResult, HttpServletRequest request
                                , HttpServletResponse response) {

        if (bindingResult.hasErrors()){
            log.error("表单验证出错");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ServerResponse.createByErrorCodeMessage(ResponseCode.ERROR.getCode(),
                    bindingResult.getFieldError().getDefaultMessage());
        }
        //jwt验证
        if (!tokenCheckHandler.checkToken(form.getId(), form.getToken(), request)){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ServerResponse.createByErrorCodeMessage(ResponseCode.ERROR.getCode(),
                    "token验证错误");
        }
        ChatterDTO chatterDTO = new ChatterDTO();
        BeanUtils.copyProperties(form, chatterDTO);

        try {
            chatterService.update(chatterDTO);
        } catch (ChatterServiceException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return ServerResponse.createByErrorCodeMessage(e.getCode(), e.getMessage());
        }
        return ServerResponse.createBySuccess();
    }

    @PostMapping("/getChatterListById")
    private ServerResponse<List<ChatterDTO>> getChatterListById( @RequestParam("chatterId") String chatterId,
            @RequestParam("chatterIdList") String chatterIdListStr,
            @RequestParam("token") String token,
            HttpServletRequest request,
            HttpServletResponse response) {
        //jwt验证
        if (!tokenCheckHandler.checkToken(chatterId, token, request)){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ServerResponse.createByErrorCodeMessage(ResponseCode.ERROR.getCode(),
                    "token验证错误");
        }
        List<String> chatterIdList = JSONObject.parseArray(chatterIdListStr, String.class);
        if (CollectionUtils.isEmpty(chatterIdList)) {
            return ServerResponse.createBySuccess();
        }
        List<ChatterDTO> chatterDTOList = new ArrayList<>(chatterIdList.size());
        for (String id : chatterIdList) {
            ChatterDTO chatterDTO = chatterService.selectById(id);
            if (null != chatterDTO) {
                chatterDTOList.add(chatterDTO);
            }
        }
        return ServerResponse.createBySuccess(chatterDTOList);
    }

    /**
     * 客户端用来检测服务端状态的url
     * @return
     */
    @GetMapping("/heartBeat")
    private ServerResponse heartBeat(@RequestParam("chatterId") String chatterId,
                                     @RequestParam("token") String token,
                                     HttpServletRequest request,
                                     HttpServletResponse response){

        //jwt验证
        if (!tokenCheckHandler.checkToken(chatterId, token, request)){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ServerResponse.createByErrorCodeMessage(ResponseCode.ERROR.getCode(),
                    "token验证错误");
        }
        //设置不缓存,为了在离线时立刻判断
        response.setHeader("Cache-Control","no-cache");
        response.setHeader("Pragma","no-cache");
        response.setDateHeader("Expires",0);

        //检查是否存在用户
        ChatterDTO dto = chatterService.selectById(chatterId);
        if (null == dto){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ServerResponse.createByErrorMessage("no-user-exist");
        }

        // 检查服务器是否存在
        ChatServer server = serverService.selectByChatterId(chatterId);
        if (null == server){
            return ServerResponse.createByErrorMessage("no-server-exist");
        }
        //判断ip地址是否发生改变，改变则通知重连
        String currentIp = IpUtils.getIp(request);
        if (!currentIp.equalsIgnoreCase(dto.getIp())){
            return ServerResponse.createByErrorMessage("reconnect");
        }

        //设置为在线
        if (dto.getStatus() != ChatterStatusEnum.ONLINE.getCode()){
            chatterService.setChatterStatus(chatterId, ChatterStatusEnum.ONLINE.getCode());
        }
        //判断当前websocket状态，如果中断连接，则重新初始化
        //ChatServer server = serverService.selectByChatterId(chatterId);

        return ServerResponse.createBySuccess();
    }

    /**
     * 删除先前存在的chatter
     * @return
     */
    @DeleteMapping
    public ServerResponse deletePreChatter(@RequestParam("preId") String preId) {
        if (preId.length() == 0) {
            return ServerResponse.createBySuccess();
        }
        ChatterDTO chatterDTO = new ChatterDTO();
        chatterDTO.setId(preId);
        chatterService.remove(chatterDTO);
        Integer closeSessionNum = sessionService.closeSessions(preId);
        log.info("共关闭"+closeSessionNum+"个session");
        return ServerResponse.createBySuccess();
    }

    /**
     * 重连机制，用于所有socket失效但网络连接未断的情况的情况
     * @param chatterId
     * @param request
     * @param response
     * @return
     */
    @PostMapping("/reconnect")
    private ServerResponse reconnect(@RequestParam("chatterId") String chatterId,
                                     @RequestParam("token") String token,
                                     HttpServletRequest request,
                                     HttpServletResponse response){
        // 前置判断，是否可以超过客户端最大连接数
        if (chatterService.isOnlineChatterOverflow()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            ServerResponse<Object> result = ServerResponse.createByErrorMessage("当前在线人数已达上限");
            Map<String, Boolean> rsMap = new HashMap<>();
            rsMap.put("isOverFlow", true);
            result.setData(rsMap);
            return result;
        }
        //1.判断chatter与server是否都存在
        ChatterDTO chatterDTO = chatterService.selectById(chatterId);
        ChatServer server = serverService.selectByChatterId(chatterId);
        if (null == chatterDTO){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            log.error("重连失败,chatter不存在, id = {}", chatterId);
            return ServerResponse.createByErrorMessage("重连失败,chatter不存在");
        }
        //2.判断jwt的相同
        // 判断token能否被刷新
        if (jwtUtil.canRefresh(token)) {
            token = jwtUtil.generateToken(chatterId);
        }
        if (!tokenCheckHandler.checkToken(chatterId, token, request)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            log.error("重连失败,token错误, id = {}", chatterId);
            return ServerResponse.createByErrorMessage("重连失败,token错误");
        }
        try {
            // 如果还存在server，则关闭它
            if (null != server) {
                server.onClose();
            }
        } catch (IOException | EncodeException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            log.error("重连失败,内部错误, id = {}", chatterId, e);
            return ServerResponse.createByErrorMessage("重连失败,内部错误");
        }
        //4.保存session,返回成功与chatterId，通知前端重新建立socket
        chatterDTO.setIp(IpUtils.getIp(request));
        //设置为在线
        if (chatterDTO.getStatus() != ChatterStatusEnum.ONLINE.getCode()){
            chatterDTO.setStatus(ChatterStatusEnum.ONLINE.getCode());
        }
        chatterService.save(chatterDTO);
        // 为dto设置token
        chatterDTO.setToken(token);
        return ServerResponse.createBySuccess(chatterDTO);
    }

    /**
     * 获取群聊的历史聊天者
     */
    @GetMapping("/common/chatter")
    public ServerResponse commonChatter() {
        SessionDTO session = sessionService.findSession("common-session");
        if (null == session) {
            return ServerResponse.createBySuccess(new HashSet<>());
        }
        List<ChatterDTO> result = new ArrayList<>();
        for (Chatter chatter : session.getChatterSet()) {
            ChatterDTO cur = chatterService.selectById(chatter.getId());
            if (null == cur) {
                cur = (ChatterDTO) BeanUtilsPlug.copyPropertiesReturnTarget(chatter, new ChatterDTO());
            }
            result.add(cur);
        }
        return ServerResponse.createBySuccess(result);
    }

    @GetMapping("/video/state")
    public ServerResponse videoState(@RequestParam String from , @RequestParam String to) {
        ChatterDTO fromChatter = chatterService.selectById(from);
        ChatterDTO toChatter = chatterService.selectById(to);
        if (null != fromChatter && null != toChatter) {
            if (fromChatter.getVideoState().get() != VideoStateEnum.FREE.getCode()) {
                // 自己在通话中
                return ServerResponse.createByErrorMessage("自己正在通话中");
            }
            if (toChatter.getVideoState().get() != VideoStateEnum.FREE.getCode()) {
                return ServerResponse.createByErrorMessage("对方正在通话中");
            }
            return ServerResponse.createBySuccess();
        } else {
            return ServerResponse.createByErrorMessage("通话目标不存在");
        }
    }


}
