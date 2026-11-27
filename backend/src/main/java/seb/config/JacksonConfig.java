package seb.config;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 序列化配置。
 *
 * <p>{@code application.properties} 中的 {@code spring.jackson.date-format} 只对
 * 老式的 {@code java.util.Date} 生效，对 JSR-310 的 {@code LocalDateTime} 完全无效，
 * 默认会输出成 ISO-8601 的 {@code 2026-01-15T10:00:00}。
 *
 * <p>这里通过 {@link JavaTimeModule} 统一把 {@code LocalDateTime} 的
 * 序列化与反序列化格式都固定为 {@code yyyy-MM-dd HH:mm:ss}，
 * 相比在每个实体字段上加 {@code @JsonFormat} 的做法，改动集中、无需侵入实体。
 */
@Configuration
public class JacksonConfig {

    /** 全局日期时间格式，与前端 fmt() 工具的解析格式保持一致 */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    /**
     * 注册自定义的 JavaTimeModule，覆盖 Spring Boot 默认注册的同名模块中的
     * LocalDateTime 序列化器/反序列化器。
     *
     * <p>使用 {@code modulesToInstall} 而不是 {@code modules}：前者在自动探测到的
     * 模块之后追加安装，能覆盖默认实现且不会丢掉其它已注册模块。
     *
     * @return ObjectMapper 定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeCustomizer() {
        return builder -> {
            JavaTimeModule javaTimeModule = new JavaTimeModule();
            javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
            javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME_FORMATTER));
            builder.modulesToInstall(javaTimeModule);
        };
    }
}
