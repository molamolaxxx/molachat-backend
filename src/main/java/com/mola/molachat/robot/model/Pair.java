package com.mola.molachat.robot.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2025-09-27 03:26
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pair<A, B>  {
    private A first;
    private B second;

    public static Pair<String, String> of(String first, String second) {
        return new Pair<>(first, second);
    }

    public A getFirst() {
        return this.first;
    }

    public B getSecond() {
        return this.second;
    }
}
