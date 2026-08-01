package com.mola.molachat.team.solution;

import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.session.model.StreamMessage;
import com.mola.molachat.session.solution.MessageSolution;
import com.mola.molachat.team.dto.TeamEventDTO;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TeamMessageEventSolutionTest {

    @Mock
    private ChatterFactoryInterface chatterFactory;

    @Mock
    private MessageSolution messageSolution;

    @InjectMocks
    private TeamMessageEventSolution eventSolution;

    @Before
    public void setUp() {
        RobotChatter robot = new RobotChatter();
        robot.setId("team-acp-member-1");
        robot.setVisibleChatterIds(Collections.singleton("owner"));
        when(chatterFactory.select("team-acp-member-1")).thenReturn(robot);
    }

    @Test
    public void chunkRoutesToDeterministicOwnerRobotSession() {
        eventSolution.handle(event("MESSAGE_CHUNK", "{\"content\":\"hello\"}"));

        ArgumentCaptor<StreamMessage> captor = ArgumentCaptor.forClass(StreamMessage.class);
        verify(messageSolution).sendStreamMessage(
                org.mockito.ArgumentMatchers.eq("ownerteam-acp-member-1"), captor.capture());
        assertEquals("hello", captor.getValue().getContent());
        assertFalse(captor.getValue().isEnd());
    }

    @Test
    public void completeEndsCurrentStream() {
        eventSolution.handle(event("MESSAGE_COMPLETE", "{}"));

        ArgumentCaptor<StreamMessage> captor = ArgumentCaptor.forClass(StreamMessage.class);
        verify(messageSolution).sendStreamMessage(
                org.mockito.ArgumentMatchers.anyString(), captor.capture());
        assertTrue(captor.getValue().isEnd());
    }

    @Test
    public void teamTalkToCardHtmlIsForwardedWithoutRewriting() {
        String card = "<details class=\"tool-call team-talk-to-card\" open "
                + "data-team-member-id=\"member-2\"><summary>📤 发送 Team 消息</summary>"
                + "<div class=\"tool-call-body\">路由 target：member-2</div></details>";
        eventSolution.handle(event("MESSAGE_CHUNK", "{\"content\":"
                + com.alibaba.fastjson.JSON.toJSONString(card)
                + ",\"cardType\":\"TEAM_TALK_TO\",\"direction\":\"SEND\","
                + "\"delivery\":\"DELIVERED\"}"));

        ArgumentCaptor<StreamMessage> captor = ArgumentCaptor.forClass(StreamMessage.class);
        verify(messageSolution).sendStreamMessage(
                org.mockito.ArgumentMatchers.anyString(), captor.capture());
        assertEquals(card, captor.getValue().getContent());
        assertFalse(captor.getValue().isEnd());
    }

    @Test
    public void errorContentIsForwardedExactlyLikeOrdinaryAcpEndFrame() {
        String content = "====== 发生错误 ======\nprovider failed";
        eventSolution.handle(event("MESSAGE_ERROR", "{\"code\":\"INTERNAL_ERROR\","
                + "\"message\":\"provider failed\",\"retryable\":true,\"content\":"
                + com.alibaba.fastjson.JSON.toJSONString(content) + "}"));

        ArgumentCaptor<StreamMessage> captor = ArgumentCaptor.forClass(StreamMessage.class);
        verify(messageSolution).sendStreamMessage(
                org.mockito.ArgumentMatchers.anyString(), captor.capture());
        assertEquals(content, captor.getValue().getContent());
        assertTrue(captor.getValue().isEnd());
    }

    private TeamEventDTO event(String type, String data) {
        TeamEventDTO event = new TeamEventDTO();
        event.setSchemaVersion("1");
        event.setEventId("event-1");
        event.setTeamId("team-1");
        event.setTeamMemberId("member-1");
        event.setAcpClientId("team-acp-member-1");
        event.setType(type);
        event.setData(data);
        return event;
    }
}
