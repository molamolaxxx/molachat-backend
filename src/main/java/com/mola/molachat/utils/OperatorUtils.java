package com.mola.molachat.utils;

import java.util.*;

/**
 * @author : molamola
 * @Project: leetcode
 * @Description: 根据符号优先级计算表达式结果
 * @date : 2021-03-29 16:04
 **/
public class OperatorUtils {

    private static Map<Character, Integer> operatorPriorityMap = new HashMap<>();

    static {
        operatorPriorityMap.put('(',Integer.MAX_VALUE);
        operatorPriorityMap.put(')',Integer.MIN_VALUE);
        operatorPriorityMap.put('+',3);
        operatorPriorityMap.put('-',3);
        operatorPriorityMap.put('*',4);
        operatorPriorityMap.put('/',4);
        operatorPriorityMap.put('p',5);
        operatorPriorityMap.put('|',-1);
        operatorPriorityMap.put('^',-1);
        operatorPriorityMap.put('&',100);
        operatorPriorityMap.put('E', Integer.MIN_VALUE);
    }

    public static Object operate(String raw) {
        Deque<Double> numStack = new ArrayDeque<>();
        Deque<Character> operatorStack = new ArrayDeque<>();
        StringBuffer sb = new StringBuffer();
        final char[] chars = raw.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (operatorPriorityMap.keySet().contains(chars[i])) { // 操作符
                if (sb.toString().length() != 0) {
                    addNumToStack(sb.toString(), numStack);
                    sb.delete(0, sb.length());
                }
                addOperatorToStack(chars[i], operatorStack, numStack);
            } else if (chars[i]-'0' >= 0 && chars[i]-'0' <= 9 || chars[i] == '.') { // 操作数
                sb.append(chars[i]);
                if (i == chars.length - 1) {
                    addNumToStack(sb.toString(), numStack);
                }
            } else { // 不存在
                throw new IllegalArgumentException("符号错误，不存在符号" + chars[i]);
            }
        }
        // 压入结束符，结束所有运算
        addOperatorToStack('E', operatorStack, numStack);
        Double last = numStack.getLast();
        if (last.intValue() == last) {
            return last.intValue();
        }
        return last;
    }

    /**
     * 操作数进栈
     * @param numStr
     * @param numStack
     */
    private static void addNumToStack(String numStr, Deque<Double> numStack) {
        double num = Double.parseDouble(numStr);
        numStack.addLast(num);
    }

    /**
     * 操作符进栈
     * @param operator
     * @param operatorStack
     */
    private static void addOperatorToStack(Character operator, Deque<Character> operatorStack, Deque<Double> numStack) {
        Integer priorityCurrent = operatorPriorityMap.get(operator);
        if (priorityCurrent == null) {
            throw new IllegalArgumentException("符号错误，不存在符号" + operator);
        }
        // 如果优先级比栈顶操作符优先级大，则直接入站
        if (operatorStack.size() == 0 || operatorPriorityMap.get(operatorStack.getLast()) < priorityCurrent) {
            operatorStack.addLast(operator);
            return;
        }

        // 小则弹出栈顶操作符，进行计算，压入操作数栈
        while (operatorStack.size() != 0 && operatorPriorityMap.get(operatorStack.getLast()) >= priorityCurrent) {
            // 判断是否是括号配对
            if (operatorStack.getLast() == '(') {
                if (operator == ')') { // 左右括号相互抵消
                    operatorStack.pollLast();
                    return;
                }
                break; // 所有入栈符号可以直接无视左括号
            }
            Character opsLast = operatorStack.pollLast();

            if (numStack.size() < 2) {
                throw new IllegalArgumentException("表达式错误");
            }
            double y = numStack.pollLast();
            double x = numStack.pollLast();
            numStack.addLast(calculate(x, y, opsLast));
        }
        operatorStack.addLast(operator);
    }

    private static double calculate(double x, double y, Character ops) {
        switch (ops) {
            case '+': {
                return x+y;
            }
            case '-': {
                return x-y;
            }
            case '*': {
                return x*y;
            }
            case '/': {
                return x/y;
            }
            case 'p': {
                return Math.pow(x,y);
            }
            case '|': {
                return (int)x|(int)y;
            }
            case '&': {
                return (int)x&(int)y;
            }
            case '^': {
                return (int)x^(int)y;
            }
        }
        return -1;
    }
}
