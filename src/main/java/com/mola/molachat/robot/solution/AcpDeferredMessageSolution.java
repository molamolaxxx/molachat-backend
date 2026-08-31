package com.mola.molachat.robot.solution;

import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.solution.MessageSolution;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Holds BUSY user messages until the interrupted old turn reaches its terminal boundary. */
@Service
public class AcpDeferredMessageSolution {

    @Resource
    private MessageSolution messageSolution;

    private final Map<String, List<Message>> deferredByGroup = new LinkedHashMap<>();

    public synchronized boolean defer(String groupId, Message message) {
        if (!messageSolution.removeMessage(groupId, message)) {
            return false;
        }
        deferredByGroup.computeIfAbsent(groupId, ignored -> new ArrayList<>()).add(message);
        return true;
    }

    public void restore(String groupId, Message message) {
        synchronized (this) {
            List<Message> deferred = deferredByGroup.get(groupId);
            if (deferred == null || !deferred.remove(message)) {
                return;
            }
            if (deferred.isEmpty()) {
                deferredByGroup.remove(groupId);
            }
        }
        messageSolution.insertMessageWithoutRobot(groupId, message);
    }

    public void flush(String groupId) {
        List<Message> deferred;
        synchronized (this) {
            deferred = deferredByGroup.remove(groupId);
        }
        if (deferred == null) {
            return;
        }
        for (Message message : deferred) {
            messageSolution.insertMessageWithoutRobot(groupId, message);
        }
    }
}
