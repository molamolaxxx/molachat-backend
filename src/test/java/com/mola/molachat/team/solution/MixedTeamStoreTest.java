package com.mola.molachat.team.solution;

import com.mola.molachat.robot.data.KeyValueFactoryInterface;
import com.mola.molachat.robot.model.KeyValue;
import com.mola.molachat.team.model.MixedTeamRecord;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MixedTeamStoreTest {

    @Mock
    private KeyValueFactoryInterface keyValueFactory;

    @InjectMocks
    private MixedTeamStore store;

    @Test
    public void savesBeforeRpcWithOwnerScopedKey() {
        MixedTeamRecord record = record();

        store.save(record);

        ArgumentCaptor<KeyValue> captor = ArgumentCaptor.forClass(KeyValue.class);
        verify(keyValueFactory).save(captor.capture());
        assertEquals("fast-team.mixed.team-1", captor.getValue().getKey());
        assertEquals("owner", captor.getValue().getOwner());
        assertNotNull(record.getCreatedAt());
        assertNotNull(record.getUpdatedAt());
    }

    @Test
    public void listsOnlyOwnerMixedRecords() {
        MixedTeamRecord ownerRecord = record();
        MixedTeamRecord otherRecord = record();
        otherRecord.setTeamId("team-2");
        otherRecord.setOwnerChatterId("other");
        when(keyValueFactory.list()).thenReturn(Arrays.asList(
                KeyValue.builder().key("fast-team.mixed.team-1")
                        .owner("owner").value(com.alibaba.fastjson.JSON.toJSONString(ownerRecord)).build(),
                KeyValue.builder().key("fast-team.mixed.team-2")
                        .owner("other").value(com.alibaba.fastjson.JSON.toJSONString(otherRecord)).build(),
                KeyValue.builder().key("unrelated").owner("owner").value("{}").build()));

        assertEquals(1, store.listByOwner("owner").size());
        assertEquals("team-1", store.listByOwner("owner").get(0).getTeamId());
    }

    private MixedTeamRecord record() {
        MixedTeamRecord record = new MixedTeamRecord();
        record.setTeamId("team-1");
        record.setOwnerChatterId("owner");
        record.setState("CREATING");
        return record;
    }
}
