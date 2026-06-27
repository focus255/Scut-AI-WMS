/**
 * AI 大模型调用服务——通过 HTTP 调用 OpenAI 兼容 API 进行库存智能分析。
 *
 * @author Focus
 * @date 2026-06-24
 */
package com.smartwms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.llm.api-key:demo-api-key}")
    private String apiKey;

    @Value("${ai.llm.base-url:https://api.llm-provider.com/v1}")
    private String baseUrl;

    @Value("${ai.llm.model:deepseek-chat}")
    private String model;

    public AIService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 调用大模型进行库存风险分析。
     *
     * @param systemPrompt  系统角色设定
     * @param userPrompt    用户输入（含库存数据）
     * @return 解析后的 JSON 响应，调用失败返回 null
     */
    public JsonNode chat(String systemPrompt, String userPrompt) {
        try {
            String url = baseUrl + "/chat/completions";

            Map<String, Object> requestBody = Map.of(
                "model", model,
                "temperature", 0.3,
                "max_tokens", 1024,
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
                )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            log.info("[AI-API] 调用 {} 模型, baseUrl={}", model, baseUrl);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String content = root.path("choices").get(0)
                        .path("message").path("content").asText();
                return extractJson(content);
            }
        } catch (Exception e) {
            log.error("[AI-API] 调用失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从 LLM 响应文本中提取 JSON 块，多种策略容错。
     */
    private JsonNode extractJson(String content) {
        String text = content.trim();
        if (text.isEmpty()) {
            log.warn("[AI-API] 响应内容为空");
            return null;
        }

        // 策略1: 纯 JSON 直接解析
        if (text.startsWith("{")) {
            try { return objectMapper.readTree(text); } catch (Exception ignored) {}
        }

        // 策略2: ```json ... ``` 包裹
        int jsonStart = text.indexOf("```json");
        if (jsonStart < 0) jsonStart = text.indexOf("```");
        if (jsonStart >= 0) {
            int codeStart = text.indexOf("\n", jsonStart);
            int codeEnd = text.indexOf("```", codeStart > 0 ? codeStart : jsonStart + 3);
            if (codeStart > 0 && codeEnd > codeStart) {
                String inner = text.substring(codeStart, codeEnd).trim();
                try {
                    return objectMapper.readTree(inner);
                } catch (Exception ignored) {}
            }
        }

        // 策略3: 查找第一个 { 到最后一个 }（处理带前缀文本的 JSON）
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            String candidate = text.substring(firstBrace, lastBrace + 1);
            try {
                return objectMapper.readTree(candidate);
            } catch (Exception ignored) {}
        }

        // 策略4: 处理 DeepSeek think 块后的 JSON
        int thinkEnd = text.indexOf("<｜end▁of▁thinking｜>");
        if (thinkEnd < 0) thinkEnd = text.indexOf("\n\n", text.indexOf("<｜end▁of▁thinking｜>") > 0 ? text.indexOf(" response") : 0);
        if (thinkEnd > 0) {
            String afterThink = text.substring(thinkEnd).trim();
            try {
                return objectMapper.readTree(afterThink);
            } catch (Exception ignored) {
                // 可能还有前缀，尝试找 {
                int b2 = afterThink.indexOf('{');
                if (b2 >= 0) {
                    try { return objectMapper.readTree(afterThink.substring(b2)); }
                    catch (Exception ignored2) {}
                }
            }
        }

        log.warn("[AI-API] JSON解析失败，响应前300字: {}", text.substring(0, Math.min(300, text.length())));
        return null;
    }

    /** 判断是否已配置有效的 API Key */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && !"demo-api-key".equals(apiKey);
    }

    /** 获取当前使用的模型名称 */
    public String getModelName() { return model; }
}
