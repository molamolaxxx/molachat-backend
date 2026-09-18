package com.mola.molachat.robot.solution;

import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.session.model.Session;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class FilePreviewSolutionTest {

    @Mock
    private ChatterFactoryInterface chatterFactory;

    @InjectMocks
    private FilePreviewSolution filePreviewSolution;

    @Test
    public void renderModeUsesExtensionAndMediaType() {
        assertEquals("MARKDOWN", FilePreviewSolution.renderMode("README.md", "text/plain"));
        assertEquals("HTML", FilePreviewSolution.renderMode("page", "text/html"));
        assertEquals("CODE", FilePreviewSolution.renderMode("Service.java", "text/plain"));
        assertEquals("TEXT", FilePreviewSolution.renderMode("server.log", "text/plain"));
    }

    @Test
    public void languageMappingIsStable() {
        assertEquals("java", FilePreviewSolution.language("Service.java"));
        assertEquals("javascript", FilePreviewSolution.language("app.js"));
        assertEquals("yaml", FilePreviewSolution.language("config.yml"));
        assertNull(FilePreviewSolution.language("README"));
    }

    @Test
    public void resolvesAcpRobotFromAuthoritativeChatterFactory() {
        Chatter sessionMember = new Chatter();
        sessionMember.setId("acp-Code_Chat_Dev");
        Session session = new Session();
        session.setChatterSet(Collections.singleton(sessionMember));
        RobotChatter robot = new RobotChatter();
        robot.setId(sessionMember.getId());
        robot.setRobotGroup("acp");
        when(chatterFactory.select(sessionMember.getId())).thenReturn(robot);

        RobotChatter resolved = filePreviewSolution.requireAcpRobot(session);

        assertEquals(robot, resolved);
    }
}
