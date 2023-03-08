package com.mola.molachat.schedule;

import com.mola.molachat.config.AppConfig;
import com.mola.molachat.entity.dto.ChatterDTO;
import com.mola.molachat.enumeration.ChatterStatusEnum;
import com.mola.molachat.enumeration.ChatterTagEnum;
import com.mola.molachat.service.ChatterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-04-30 10:24
 **/
@Configuration
@EnableScheduling
@Slf4j
public class ChatterScheduleTask {

    private static final float DELETE_RATE = 0.5f;

    @Resource
    private ChatterService chatterService;

    @Resource
    private AppConfig appConfig;

    /**
     * 检查chatter最后在线时间，删除长时间不在线的chatter
     */
    @Scheduled(fixedRate = 60000*10)
    private void deleteChatters() {
        List<ChatterDTO> chatters = chatterService.list();
        // 如果chatter个数小于等于最大保留的chatter个数，则不用执行删除操作
        if (appConfig.getMaxRemainChatterCount() >= chatters.size()) {
            return;
        }
        // 获得逻辑删除的阈值
        Integer threshold = getDeleteThreshold(chatters).intValue();
        log.info("check:开始检查长时间离线chatter，逻辑删除阈值为：{}",threshold);
        for (ChatterDTO chatter : chatters) {
            if (chatter.isRobot() || chatter.getTag() != ChatterTagEnum.VISITOR.getCode()) {
                continue;
            }
            Integer point = chatter.getPoint();
            // 获取初始得分
            point *= getInitPoint(chatter);
            Long lastOnline = chatter.getLastOnline();
            // 分数低于阈值且当前不在线且已过保护时间
            if (point < threshold
                    && chatter.getStatus() != ChatterStatusEnum.ONLINE.getCode()
                    && !isNewChatter(chatter)) {
                chatterService.setChatterStatus(chatter.getId(), ChatterStatusEnum.LOGICAL_DELETE.getCode());
            }
            // 3天不在线，且任然未达到阈值,直接删除
            if (System.currentTimeMillis() - lastOnline > 3*24*60*60*1000){
                if (chatter.getStatus() == ChatterStatusEnum.LOGICAL_DELETE.getCode()) {
                    chatterService.remove(chatter);
                }
            }
            // 分数衰减，一天未登录=》每一次检查-1*未登录天数
            pointDecay(chatter);
        }
    }

    private Float getDeleteThreshold(List<ChatterDTO> chatters) {
        if (chatters.size() == 0) {
            return 0f;
        }
        Integer sum = 0;
        // 过滤
        chatters = chatters.stream()
                .filter(chatterDTO -> !(chatterDTO.isRobot() ||
                        chatterDTO.getTag() != ChatterTagEnum.VISITOR.getCode()))
                .collect(Collectors.toList());
        if (chatters.size() == 0) return 0f;
        for (ChatterDTO chatterDTO : chatters) {
            Integer point = chatterDTO.getPoint();
            sum += point;
        }
        return sum/chatters.size() * DELETE_RATE;
    }

    private Integer getInitPoint(ChatterDTO chatterDTO) {
        Integer rs = 1;
        if (isUserChangeDefaultName(chatterDTO.getName())) {
            rs *= 2;
        }
//        if (!chatterDTO.getImgUrl().endsWith("mola.png")) {
//            rs *= 2;
//        }
        if (!chatterDTO.getSignature().equals("点击修改签名")) {
            rs *= 2;
        }
        return rs;
    }

    private void pointDecay(ChatterDTO chatterDTO) {
        long distance = System.currentTimeMillis() - chatterDTO.getLastOnline();
        int day = (int) (distance/(24*60*60*1000));
        if (day <= 3) { // 心跳3天以内免除惩罚
            return;
        }
        chatterService.addPoint(chatterDTO.getId(), -1*day);
    }

    /**
     * 是否是新chatter，是则有一天保护期
     */
    private boolean isNewChatter(ChatterDTO chatterDTO) {
        long distance = System.currentTimeMillis() - chatterDTO.getCreateTime().getTime();
        int protectTime = 1;
        // 对改过昵称的游客，设置三天保护期
        if (!StringUtils.isEmpty(chatterDTO.getName()) &&
                isUserChangeDefaultName(chatterDTO.getName())) {
            protectTime = 3;
        }
        int day = (int) (distance/(24*60*60*1000));
        if (day <= protectTime) {
            return true;
        }
        return false;
    }

    /**
     * 此处认为，不带默认名随机后缀的就是改过了名字
     * @return
     */
    private boolean isUserChangeDefaultName(String name) {
        int start = name.indexOf("_");
        if (name.length()-1 != start + 5) {
            return true;
        }
        for (int i = 0; i < 5; i++) {
            char t = name.toCharArray()[start+i+1];
            int conditionNum1 = t - 'a';
            int conditionNum2 = t - 'A';
            int conditionNum3 = t - '0';
            if (conditionNum1 >= 0 && conditionNum1 <= 26 ||
                conditionNum2 >= 0 && conditionNum2 <= 26 ||
                conditionNum3 >= 0 && conditionNum3 <= 9) {
                continue;
            }
            return true;
        }
        return false;
    }
}
