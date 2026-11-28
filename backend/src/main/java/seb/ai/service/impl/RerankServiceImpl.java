package seb.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import seb.ai.service.RerankService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link RerankService} 的实现：调用硅基流动的 {@code /v1/rerank}。
 *
 * <p>该接口不是 OpenAI 兼容协议的一部分，Spring AI 也没有对应的抽象，
 * 所以这里直接用 {@link RestClient} 发请求。请求体形如：
 * <pre>
 * {"model":"BAAI/bge-reranker-v2-m3","query":"...","documents":["..."],"top_n":4}
 * </pre>
 * 响应里 {@code results[].index} 指向入参 documents 的下标，
 * {@code relevance_score} 是 0~1 的相关度。
 *
 * <p>超时给得比较短（连接 2s / 读取 3s）：重排处在「用户已经等了检索」的链路上，
 * 它慢就等于整个问答慢。超时后返回空列表让调用方降级，比拖死请求好。
 */
@Service
public class RerankServiceImpl implements RerankService {

    private static final Logger log = LoggerFactory.getLogger(RerankServiceImpl.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public RerankServiceImpl(@Value("${spring.ai.openai.base-url:https://api.siliconflow.cn}") String baseUrl,
                             @Value("${spring.ai.openai.api-key:}") String apiKey,
                             @Value("${ferry.ai.rerank-model:BAAI/bge-reranker-v2-m3}") String model) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public List<Score> rerank(String query, List<String> documents, int topN) {
        if (documents == null || documents.isEmpty() || topN <= 0) {
            return List.of();
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[rerank] 未配置 SILICONFLOW_API_KEY，跳过重排，退回向量序");
            return List.of();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("query", query);
        body.put("documents", documents);
        body.put("top_n", Math.min(topN, documents.size()));
        // 不要回传原文：原文我们手里就有，让服务端多传一遍只是浪费带宽
        body.put("return_documents", false);

        try {
            JsonNode root = restClient.post()
                    .uri("/v1/rerank")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null || !root.has("results")) {
                log.warn("[rerank] 响应缺少 results 字段，退回向量序");
                return List.of();
            }

            List<Score> scores = new ArrayList<>();
            for (JsonNode item : root.get("results")) {
                int index = item.path("index").asInt(-1);
                double score = item.path("relevance_score").asDouble(0);
                // index 越界说明服务端返回了脏数据，丢掉比越界取值安全
                if (index >= 0 && index < documents.size()) {
                    scores.add(new Score(index, score));
                }
            }
            scores.sort(Comparator.comparingDouble(Score::score).reversed());
            return scores.size() > topN ? new ArrayList<>(scores.subList(0, topN)) : scores;
        } catch (Exception e) {
            // 不打堆栈全文，避免刷屏；重排失败是预期内的降级路径
            log.warn("[rerank] 调用失败，退回向量序：{}", e.getMessage());
            return List.of();
        }
    }
}
