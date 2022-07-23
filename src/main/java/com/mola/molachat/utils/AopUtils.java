package com.mola.molachat.utils;

import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.Assert;

import java.lang.reflect.Method;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-05-01 13:42
 **/
public class AopUtils {

    /**
     * 解析el表达式
     * @param rawKey 原始的key表达式
     * @param method
     * @param args
     * @return
     */
    public static Object finalKeyResolving(String rawKey, Method method,
                                           Object[] args, Boolean allowKeyEmpty){
        ExpressionParser parser = new SpelExpressionParser();
        Expression expression = parser.parseExpression(rawKey);
        // 获取传入参数
        LocalVariableTableParameterNameDiscoverer discoverer = new LocalVariableTableParameterNameDiscoverer();
        // 获取参数名
        String[] parameterNames = discoverer.getParameterNames(method);

        Assert.isTrue(parameterNames.length == args.length, "k-v length is not match");
        // 传入参数键值对
        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameterNames.length ; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }
        Object spelResult = null;
        try {
            spelResult = expression.getValue(context);
        } catch (EvaluationException e) {
            return rawKey;
        }
        if (!allowKeyEmpty && null == spelResult){
            throw new RuntimeException("spel running with an empty result!");
        }
        return spelResult;
    }

    public static void main(String[] args) {
        ExpressionParser parser = new SpelExpressionParser();
        // 变量前必须加#
        Expression expression = parser.parseExpression("#status and #type");
        EvaluationContext context = new StandardEvaluationContext();
        context.setVariable("status", true);
        context.setVariable("type", true);
        System.out.println(expression.getValue(context));
    }
}
