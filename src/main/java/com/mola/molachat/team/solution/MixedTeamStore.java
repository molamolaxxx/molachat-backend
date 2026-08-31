package com.mola.molachat.team.solution;

import com.alibaba.fastjson.JSON;
import com.mola.molachat.robot.data.KeyValueFactoryInterface;
import com.mola.molachat.robot.model.KeyValue;
import com.mola.molachat.team.model.MixedTeamRecord;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MixedTeamStore {

    static final String KEY_PREFIX = "fast-team.mixed.";

    @Resource
    private KeyValueFactoryInterface keyValueFactory;

    public void save(MixedTeamRecord record) {
        if (record == null || StringUtils.isBlank(record.getTeamId())
                || StringUtils.isBlank(record.getOwnerChatterId())) {
            throw new IllegalArgumentException("MixedTeamRecord缺少teamId或ownerChatterId");
        }
        record.setUpdatedAt(System.currentTimeMillis());
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(record.getUpdatedAt());
        }
        keyValueFactory.save(KeyValue.builder()
                .key(KEY_PREFIX + record.getTeamId())
                .value(JSON.toJSONString(record))
                .owner(record.getOwnerChatterId())
                .desc("Fast Team mixed placement")
                .share(false)
                .build());
    }

    public MixedTeamRecord find(String teamId) {
        if (StringUtils.isBlank(teamId)) {
            return null;
        }
        KeyValue value = keyValueFactory.selectOne(KEY_PREFIX + teamId);
        return value == null || StringUtils.isBlank(value.getValue())
                ? null : JSON.parseObject(value.getValue(), MixedTeamRecord.class);
    }

    public List<MixedTeamRecord> listByOwner(String ownerChatterId) {
        List<KeyValue> values = keyValueFactory.list();
        if (values == null) {
            return Collections.emptyList();
        }
        return values.stream()
                .filter(value -> value != null && value.getKey() != null
                        && value.getKey().startsWith(KEY_PREFIX)
                        && ownerChatterId.equals(value.getOwner())
                        && StringUtils.isNotBlank(value.getValue()))
                .map(value -> JSON.parseObject(value.getValue(), MixedTeamRecord.class))
                .collect(Collectors.toList());
    }

    public void remove(String teamId) {
        if (StringUtils.isNotBlank(teamId)) {
            keyValueFactory.remove(KEY_PREFIX + teamId);
        }
    }
}
