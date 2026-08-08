package com.mola.molachat.robot.handler.impl.acp;

import com.mola.molachat.chatter.dto.ChatterDTO;
import com.mola.molachat.chatter.enums.ChatterStatusEnum;
import com.mola.molachat.chatter.service.ChatterService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AcpExecHandlerTest {

    @Mock
    private ChatterService chatterService;

    @InjectMocks
    private AcpExecHandler handler;

    @Test
    public void sessionTransitionImmediatelyShowsDisconnectedStatus() {
        ChatterDTO acp = new ChatterDTO();
        acp.setId("acp-codex");
        acp.setStatus(ChatterStatusEnum.ONLINE.getCode());
        when(chatterService.selectById("acp-codex")).thenReturn(acp);

        handler.markAcpTransitioning("acp-codex");

        verify(chatterService).setChatterStatus(
                "acp-codex", ChatterStatusEnum.DISCONNECT.getCode());
    }
}
