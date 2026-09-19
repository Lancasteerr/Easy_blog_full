package com.febrie.demo_bk.audit.infrastructure.aop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class LogParamUtil {

    private final ObjectMapper objectMapper;

    /**
     * 敏感字段
     */
    private static final Set<String> SENSITIVE_FIELDS =
            new HashSet<>(Arrays.asList(
                    "password",
                    "pwd",
                    "token",
                    "accesstoken",
                    "refreshtoken",
                    "authorization",
                    "idcard",
                    "articleabstract",
                    "articlecontenthtml",
                    "articletitle",
                    "articlecontentjson"
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
    private static void maskSensitive(JsonNode node){
        if(node == null || node.isNull()){
            return;
        }

        if(node instanceof ArrayNode array){
            for(JsonNode item : array){
                maskSensitive(item);
            }
            return;
        }

        if(!(node instanceof ObjectNode json)){
            return;
        }

        json.fieldNames()
                .forEachRemaining(key -> {
                    JsonNode value = json.get(key);

                    /*
                     * 字段名统一转小写比较，避免大小写变化绕过日志脱敏。
                     */
                    if(SENSITIVE_FIELDS.contains(key.toLowerCase(Locale.ROOT))){

                        json.put(
                                key,
                                "******"
                        );

                        return;
                    }

                    /*
                     * 递归处理嵌套对象和数组，避免深层敏感字段进入操作日志。
                     */
                    maskSensitive(value);
                });
    }


    /**
     * 解析请求参数
     */
    public String parse(Object[] args) {

        if(args == null || args.length == 0) {
            return "";
        }

        ArrayNode array = objectMapper.createArrayNode();

        for(Object arg : args) {

            //过滤特殊类型
            if(ignore(arg)){
                continue;
            }

            try{

                /*
                 * 使用Jackson树模型转换参数，不修改原始参数对象。
                 */
                JsonNode json = objectMapper.valueToTree(arg);

                maskSensitive(json);

                if(json == null || json.isNull()){
                    array.addNull();
                } else if(json.isContainerNode()){
                    array.add(json);
                } else {
                    // 普通标量按字符串记录，尽量保持原有日志展示习惯。
                    array.add(String.valueOf(arg));
                }

            }catch (Exception e){
                /*
                 * 普通对象无法转换
                 */
                array.add(
                        String.valueOf(arg)
                );
            }

        }

        String res;
        try {
            res = objectMapper.writeValueAsString(array);
        } catch (JsonProcessingException e) {
            // 极端情况下序列化失败时仍返回安全的字符串，避免影响业务方法。
            res = "[]";
        }

        //长度限制
        if (res.length() > MAX_LENGTH){

            res =
                    res.substring(0,MAX_LENGTH)
                    +"...(truncated)";
        }

        return res;
    }

}
