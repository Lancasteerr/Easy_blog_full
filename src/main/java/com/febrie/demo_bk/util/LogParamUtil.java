package com.febrie.demo_bk.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LogParamUtil {

    /**
     * 敏感字段
     */
    private static final Set<String> SENSITIVE_FIELDS =
            new HashSet<>(Arrays.asList(
                    "password",
                    "pwd",
                    "token",
                    "accessToken",
                    "refreshToken",
                    "authorization",
                    "idCard",
                    "articleAbstract",
                    "articleContentHtml",
                    "articleContentMd",
                    "articleTitle"
            ));

    /**
     * 最大日志长度
     */
    private static final int MAX_LENGTH = 2000;

    /**
     * 判断是否忽略参数
     */
    private static boolean ignore(Object arg){

        if(arg == null){
            return false;
        }

        return arg instanceof MultipartFile
                || arg instanceof HttpServletRequest
                || arg instanceof HttpServletResponse;

    }

    /**
     * 递归脱敏
     */
    private static void maskSensitive(JSONObject json){

        for(String key : json.keySet()){

            Object value = json.get(key);

            /*
             * 当前字段敏感
             */
            if(SENSITIVE_FIELDS.contains(key)){

                json.put(
                        key,
                        "******"
                );

                continue;
            }

            /*
             * value为嵌套JSON对象
             */
            if(value instanceof JSONObject obj){

                maskSensitive(obj);

            }

            /*
             * value为数组
             */
            if(value instanceof JSONArray array){

                for(Object item : array){

                    if(item instanceof JSONObject obj){

                        maskSensitive(obj);
                    }
                }

            }
        }
    }


    /**
     * 解析请求参数
     */
    public static String parse(Object[] args) {

        if(args == null || args.length == 0) {
            return "";
        }

        JSONArray array = new JSONArray();

        for(Object arg : args) {

            //过滤特殊类型
            if(ignore(arg)){
                continue;
            }

            try{

                /*
                 * 转JSON对象
                 * 不修改原始参数对象
                 */
                JSONObject json =
                        (JSONObject) JSON.toJSON(arg);//toJSON将java对象转换为JSON结构，toJSONString将java对象转换为json字符串，parseObject将json字符串转换为json对象或java对象

                maskSensitive(json);

                array.add(json);

            }catch (Exception e){
                /*
                 * 普通对象无法转换
                 */
                array.add(
                        String.valueOf(arg)
                );
            }

        }

        String res = array.toJSONString();

        //长度限制
        if (res.length() > MAX_LENGTH){

            res =
                    res.substring(0,MAX_LENGTH)
                    +"...(truncated)";
        }

        return res;
    }

}
