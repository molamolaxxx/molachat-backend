package com.mola.molachat.team.solution;

import lombok.Getter;

@Getter
public class TeamCommandException extends RuntimeException {

    private final String code;

    public TeamCommandException(String code, String message) {
        super(message);
        this.code = code;
    }
}
