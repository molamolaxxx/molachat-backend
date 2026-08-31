package com.mola.molachat.chatter.service.impl;

import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.chatter.dto.ChatterDTO;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.common.config.SelfConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ChatterServiceImplTest {

    @Mock
    private ChatterFactoryInterface chatterFactory;

    @Mock
    private SelfConfig selfConfig;

    @InjectMocks
    private ChatterServiceImpl chatterService;

    @Test
    public void createWithExistingPreIdReturnsExistingChatterWithoutOverwrite() {
        Chatter existing = new Chatter();
        existing.setId("shared-chatter");
        existing.setName("original-name");
        when(chatterFactory.select("shared-chatter")).thenReturn(existing);

        ChatterDTO request = new ChatterDTO();
        request.setId("shared-chatter");
        request.setName("stale-device-name");

        ChatterDTO result = chatterService.create(request);

        assertEquals("shared-chatter", result.getId());
        assertEquals("original-name", result.getName());
        verify(chatterFactory, never()).create(org.mockito.ArgumentMatchers.any(Chatter.class));
    }
}
