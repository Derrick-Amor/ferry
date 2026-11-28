package seb.ai.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

/**
 * 向量库装配。
 *
 * <p>为什么不用 Spring AI 的自动配置：
 * {@code RedisVectorStoreAutoConfiguration} 创建的实例没有声明任何 metadata 字段，
 * 索引里就只有 {@code content} 与 {@code embedding} 两个属性。后果有两个：
 * <ol>
 *   <li>{@code filterExpression("sourceType == 2")} 无法下推——字段不在索引里，过滤不了，
 *       「只在岗位记录里检索」这个需求做不出来；</li>
 *   <li>{@code similaritySearch} 返回的 {@link org.springframework.ai.document.Document}
 *       拿不到业务 metadata，只有 {@code distance} 与 {@code vector_score}。</li>
 * </ol>
 *
 * <p>这里的做法是把 metadata 字段显式声明进 schema。改完之后索引必须重建一次：
 * 已有的 JSON 文档里 {@code docId/courseId/...} 本来就在，{@code FT.DROPINDEX}
 * （不带 DD，不删数据）后由 {@code initializeSchema} 按新 schema 重建即可。
 * <b>但新增字段（如 sourceType）例外</b>：旧文档的 JSON 里没有这个键，
 * 重建索引也补不出来，必须重写一遍文档——本项目里是删索引带 DD 后整库重建。
 *
 * <p>字段类型的选择依据：
 * <ul>
 *   <li>{@code docId / chunkId / courseId / sourceType} 用 numeric——过滤是等值比较，
 *       numeric 比 tag 少一层转义，且范围查询（如 chunkNo &gt;= n）将来也能用；</li>
 *   <li>{@code title} 用 text——只为了回传展示，不参与过滤。用 tag 的话中文标题里的
 *       标点需要转义，得不偿失。</li>
 * </ul>
 */
@Configuration
public class VectorStoreConfig {

    /**
     * 向量库客户端。
     *
     * <p>Spring AI 的 Redis 向量库用的是 Jedis 而不是 Spring Data 的 RedisTemplate，
     * 所以这里从 {@link JedisConnectionFactory} 里把连接参数取出来自己建一个池。
     * 参数与 {@code RedisVectorStoreAutoConfiguration#jedisPooled} 保持一致，
     * 避免出现「自动配置能连上、自定义的连不上」这种排查成本极高的问题。
     *
     * <p>{@code destroyMethod = "close"} 保证容器关闭时连接池被释放。
     *
     * @param connectionFactory Spring Boot 依据 {@code spring.data.redis.*} 建的连接工厂
     * @return Jedis 连接池
     */
    @Bean(destroyMethod = "close")
    public JedisPooled jedisPooled(JedisConnectionFactory connectionFactory) {
        DefaultJedisClientConfig config = DefaultJedisClientConfig.builder()
                .ssl(connectionFactory.isUseSsl())
                .clientName(connectionFactory.getClientName())
                .timeoutMillis(connectionFactory.getTimeout())
                .password(connectionFactory.getPassword())
                .build();
        return new JedisPooled(
                new HostAndPort(connectionFactory.getHostName(), connectionFactory.getPort()),
                config);
    }

    /**
     * 向量库（覆盖 Spring AI 的自动配置）。
     *
     * <p>两个细节都是踩出来的，改之前先看这里：
     * <ol>
     *   <li><b>返回类型必须写 {@link RedisVectorStore}，不能写 {@code VectorStore} 接口</b>。
     *       自动配置的方法上标着 {@code @ConditionalOnMissingBean}，而它按「Bean 方法的
     *       返回类型」匹配——声明成接口时容器里找不到 {@code RedisVectorStore} 类型的 Bean，
     *       条件不成立，自动配置照样注册，于是两个同名 Bean 撞车，启动直接
     *       {@code BeanDefinitionOverrideException}。</li>
     *   <li><b>方法名故意不叫 {@code vectorStore}</b>。即使条件判定正确，
     *       同名 Bean 也会先触发覆盖检查，报错信息还会指向自动配置，很难看出是自己写的。</li>
     * </ol>
     *
     * @param jedis          连接池
     * @param embeddingModel 由 {@code spring.ai.openai.embedding} 装配的 BAAI/bge-m3
     * @param indexName      索引名
     * @param prefix         key 前缀
     * @return 声明了业务 metadata 字段的向量库
     */
    @Bean
    public RedisVectorStore ferryVectorStore(JedisPooled jedis,
                                             EmbeddingModel embeddingModel,
                                             @Value("${spring.ai.vectorstore.redis.index-name:ferry-knowledge}") String indexName,
                                             @Value("${spring.ai.vectorstore.redis.prefix:ferry:chunk:}") String prefix) {
        return RedisVectorStore.builder(jedis, embeddingModel)
                .indexName(indexName)
                .prefix(prefix)
                .initializeSchema(true)
                .metadataFields(
                        RedisVectorStore.MetadataField.numeric("docId"),
                        RedisVectorStore.MetadataField.numeric("chunkId"),
                        RedisVectorStore.MetadataField.numeric("courseId"),
                        // 来源类型：1 课程章节 / 2 职位描述 / 3 上传文档。
                        // 岗位咨询必须靠它把课程正文挡在外面——否则问「有没有 Java 岗」
                        // 会召回一堆 Java 教程，再被当成年薪答案。
                        RedisVectorStore.MetadataField.numeric("sourceType"),
                        // courseId 可能为空，写索引时统一落 0；过滤时 0 表示「不属于任何课程」
                        RedisVectorStore.MetadataField.text("title"))
                .build();
    }
}
