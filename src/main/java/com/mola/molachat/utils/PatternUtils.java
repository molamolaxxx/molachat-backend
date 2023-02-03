package com.mola.molachat.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-02-03 20:49
 **/
public class PatternUtils {

    public static Pattern patternAll = Pattern.compile("^!\\[.*\\)");
    public static Pattern patternFileName = Pattern.compile("^!\\[.*\\]");
    public static Pattern patternUrl = Pattern.compile("(http|ftp|https):\\/\\/[\\w\\-_]+(\\.[\\w\\-_]+)+([\\w\\-\\.,@?^=%&:/~\\+#]*[\\w\\-\\@?^=%&/~\\+#])?");

    public static List<String> matchAll(String text, Pattern pattern) {
        String[] split = text.split("\n");
        List<String> res = new ArrayList<>();
        for (String str : split) {
            Matcher matcher = pattern.matcher(str);
            String mdImgUrl = null;
            while (matcher.find()) {
                res.add(matcher.group());
            }
        }
        return res;
    }

    public static String match(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        String mdImgUrl = null;
        while (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
}
