package com.mola.molachat.team.dto;

import org.junit.Test;

import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TeamCreateRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    public void singleMemberIsValid() {
        TeamCreateRequest request = validRequest();

        assertTrue(validator.validate(request).isEmpty());
        assertEquals("NORMAL", request.getMode());
    }

    @Test
    public void emptyMembersIsInvalid() {
        TeamCreateRequest request = validRequest();
        request.setMembers(Collections.emptyList());

        assertFalse(validator.validate(request).isEmpty());
    }

    private TeamCreateRequest validRequest() {
        TeamCreateRequest request = new TeamCreateRequest();
        request.setChatterId("owner");
        request.setToken("token");
        request.setRequestId("request-1");
        request.setName("Solo Team");

        TeamCreateMemberRequest member = new TeamCreateMemberRequest();
        member.setCmdProxyInstanceId("instance-1");
        member.setTransportGroup("team-acp-instance-1");
        member.setSourceRobotId("source-1");
        member.setSourceGroupId("group-1");
        request.setMembers(Collections.singletonList(member));
        return request;
    }
}
