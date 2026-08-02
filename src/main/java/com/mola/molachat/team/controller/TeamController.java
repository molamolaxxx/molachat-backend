package com.mola.molachat.team.controller;

import com.mola.molachat.common.handler.TokenCheckHandler;
import com.mola.molachat.common.model.ServerResponse;
import com.mola.molachat.team.dto.TeamCreateRequest;
import com.mola.molachat.team.dto.TeamDiscoveryDTO;
import com.mola.molachat.team.dto.TeamDTO;
import com.mola.molachat.team.dto.TeamMemberDTO;
import com.mola.molachat.team.solution.TeamCommandException;
import com.mola.molachat.team.solution.TeamGatewaySolution;
import org.apache.commons.lang.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/team")
public class TeamController {

    @Resource
    private TokenCheckHandler tokenCheckHandler;

    @Resource
    private TeamGatewaySolution teamGatewaySolution;

    @GetMapping("/capability")
    public ServerResponse<TeamDiscoveryDTO> capability(
            @RequestParam("chatterId") String chatterId,
            @RequestParam("token") String token,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (!checkToken(chatterId, token, request, response)) {
            return ServerResponse.createByErrorMessage("token验证错误");
        }
        try {
            return ServerResponse.createBySuccess(
                    teamGatewaySolution.getPlacementCapability(chatterId));
        } catch (TeamCommandException e) {
            return commandError(e, response);
        }
    }

    @GetMapping("/candidates")
    public ServerResponse<List<TeamMemberDTO>> listCandidates(
            @RequestParam("chatterId") String chatterId,
            @RequestParam("token") String token,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (!checkToken(chatterId, token, request, response)) {
            return ServerResponse.createByErrorMessage("token验证错误");
        }
        try {
            return ServerResponse.createBySuccess(
                    teamGatewaySolution.listCandidates(chatterId));
        } catch (TeamCommandException e) {
            return commandError(e, response);
        }
    }

    @GetMapping
    public ServerResponse<List<TeamDTO>> list(
            @RequestParam("chatterId") String chatterId,
            @RequestParam("token") String token,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (!checkToken(chatterId, token, request, response)) {
            return ServerResponse.createByErrorMessage("token验证错误");
        }
        try {
            return ServerResponse.createBySuccess(teamGatewaySolution.list(chatterId));
        } catch (TeamCommandException e) {
            return commandError(e, response);
        }
    }

    @GetMapping("/snapshot")
    public ServerResponse<List<TeamDTO>> snapshot(
            @RequestParam("chatterId") String chatterId,
            @RequestParam("token") String token,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (!checkToken(chatterId, token, request, response)) {
            return ServerResponse.createByErrorMessage("token验证错误");
        }
        return ServerResponse.createBySuccess(teamGatewaySolution.snapshot(chatterId));
    }

    @GetMapping("/{teamId}")
    public ServerResponse<TeamDTO> get(
            @PathVariable("teamId") String teamId,
            @RequestParam("chatterId") String chatterId,
            @RequestParam("token") String token,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (!checkToken(chatterId, token, request, response)) {
            return ServerResponse.createByErrorMessage("token验证错误");
        }
        if (StringUtils.isBlank(teamId)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ServerResponse.createByErrorMessage("teamId不能为空");
        }
        try {
            return ServerResponse.createBySuccess(teamGatewaySolution.get(chatterId, teamId));
        } catch (TeamCommandException e) {
            return commandError(e, response);
        }
    }

    @PostMapping
    public ServerResponse<TeamDTO> create(
            @Valid @RequestBody TeamCreateRequest createRequest,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ServerResponse.createByErrorMessage(bindingResult.getFieldError().getDefaultMessage());
        }
        if (!checkToken(createRequest.getChatterId(), createRequest.getToken(), request, response)) {
            return ServerResponse.createByErrorMessage("token验证错误");
        }
        createRequest.setName(StringUtils.trim(createRequest.getName()));
        if (StringUtils.isBlank(createRequest.getName())) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ServerResponse.createByErrorMessage("队伍名称不能为空");
        }
        try {
            return ServerResponse.createBySuccess(teamGatewaySolution.create(createRequest));
        } catch (TeamCommandException e) {
            return commandError(e, response);
        }
    }

    @DeleteMapping("/{teamId}")
    public ServerResponse<Object> delete(
            @PathVariable("teamId") String teamId,
            @RequestParam("chatterId") String chatterId,
            @RequestParam("token") String token,
            @RequestParam("requestId") String requestId,
            @RequestParam("expectedVersion") Long expectedVersion,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (!checkToken(chatterId, token, request, response)) {
            return ServerResponse.createByErrorMessage("token验证错误");
        }
        if (StringUtils.isBlank(teamId) || StringUtils.isBlank(requestId)
                || expectedVersion == null || expectedVersion < 0) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ServerResponse.createByErrorMessage(
                    "teamId、requestId和expectedVersion不能为空");
        }
        try {
            return ServerResponse.createBySuccess(
                    teamGatewaySolution.delete(chatterId, teamId, requestId, expectedVersion));
        } catch (TeamCommandException e) {
            return commandError(e, response);
        }
    }

    private boolean checkToken(String chatterId, String token, HttpServletRequest request,
                               HttpServletResponse response) {
        if (tokenCheckHandler.checkToken(chatterId, token, request)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return false;
    }

    private <T> ServerResponse<T> commandError(TeamCommandException exception,
                                               HttpServletResponse response) {
        String code = exception.getCode();
        if ("NOT_FOUND".equals(code)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } else if ("UNAUTHORIZED".equals(code)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        } else if ("IDEMPOTENCY_CONFLICT".equals(code)
                || "VERSION_CONFLICT".equals(code)
                || "TEAM_DELETING".equals(code)
                || "CMD_PROXY_INSTANCE_CONFLICT".equals(code)) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
        } else if ("TEAM_NOT_READY".equals(code)) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } else if ("INTERNAL_ERROR".equals(code)) {
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        return ServerResponse.createByErrorMessage(
                friendlyCommandError(code, exception.getMessage()));
    }

    private String friendlyCommandError(String code, String message) {
        if ("SOURCE_ROBOT_NOT_FOUND".equals(code)) {
            return "所选ACP成员已离线或配置已失效，请刷新成员列表后重新选择";
        }
        if ("SOURCE_ROBOT_MISMATCH".equals(code)) {
            return "所选ACP成员配置已变化，请刷新成员列表后重新选择";
        }
        if ("TEAM_NOT_READY".equals(code)) {
            return "当前环境未启用Fast Team，或运行时尚未连接完成";
        }
        if ("QUOTA_EXCEEDED".equals(code)) {
            return "Fast Team数量或成员数已达到当前环境上限";
        }
        return message;
    }
}
