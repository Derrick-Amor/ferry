package seb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动类。
 *
 * <p>扫描范围统一用根包 {@code seb}：之前用的是显式白名单
 * （seb.controller / seb.service / ...），每新增一个子包（如 seb.ai）
 * 都必须回来改主类，漏改时表现为「注入点找不到 bean」，排查成本很高。
 */
@SpringBootApplication(scanBasePackages = "seb")
@MapperScan("seb.mapper")
public class SmartEduBackendApplication {
	public static void main(String[] args) {
		SpringApplication.run(SmartEduBackendApplication.class, args);
	}

}
