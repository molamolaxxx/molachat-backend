package com.mola.molachat.utils;

import com.mola.molachat.service.http.HttpService;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.json.JSONArray;

import java.net.URLEncoder;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2022-09-12 15:52
 **/
public class TranslateUtils {
    public static String translate(String langFrom, String langTo, String word) throws Exception {
        String url = "https://translate.googleapis.com/translate_a/single?" +
                "client=gtx&" +
                "sl=" + langFrom +
                "&tl=" + langTo +
                "&dt=t&q=" + URLEncoder.encode(word, "UTF-8");
        HttpService httpService = HttpService.INSTANCE;
        Header header = new BasicHeader("User-Agent", "Mozilla/5.0");
        String res = httpService.get(url, 10000, new Header[]{header});
        return parseResult(res);

    }

    private static String parseResult(String inputJson) throws Exception {
        JSONArray jsonArray = new JSONArray(inputJson);
        JSONArray jsonArray2 = (JSONArray) jsonArray.get(0);
        String result ="";
        for(int i =0;i < jsonArray2.length();i ++){
            result += ((JSONArray) jsonArray2.get(i)).get(0).toString();
        }
        return result;
    }

    public static boolean isEnglish(String p) {
        byte[] bytes = p.getBytes();
        int i = bytes.length;//i为字节长度
        int j = p.length();//j为字符长度
        if (i * 1.0 / j > 1.5) {
            return false;
        }
        return true;
    }
}
