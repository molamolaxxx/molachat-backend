package com.mola.molachat.robot.solution;

import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.solution.MessageSolution;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AcpDeferredMessageSolutionTest {

    @Mock
    private MessageSolution messageSolution;

    @InjectMocks
    private AcpDeferredMessageSolution solution;

    @Test
    public void flushProjectsDeferredMessagesInArrivalOrderWithoutRobotDispatch() {
        Message first = message("first");
        Message second = message("second");
        when(messageSolution.removeMessage("group-1", first)).thenReturn(true);
        when(messageSolution.removeMessage("group-1", second)).thenReturn(true);

        assertTrue(solution.defer("group-1", first));
        assertTrue(solution.defer("group-1", second));
        solution.flush("group-1");

        org.mockito.InOrder order = inOrder(messageSolution);
        order.verify(messageSolution).insertMessageWithoutRobot("group-1", first);
        order.verify(messageSolution).insertMessageWithoutRobot("group-1", second);
    }

    @Test
    public void restoreReinsertsRejectedDeferredMessage() {
        Message message = message("rejected");
        when(messageSolution.removeMessage("group-1", message)).thenReturn(true);
        assertTrue(solution.defer("group-1", message));

        solution.restore("group-1", message);

        verify(messageSolution).insertMessageWithoutRobot("group-1", message);
    }

    private static Message message(String id) {
        Message message = new Message();
        message.setId(id);
        return message;
    }
}
