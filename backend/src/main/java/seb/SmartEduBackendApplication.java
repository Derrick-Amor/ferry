package seb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "seb.controller", "seb.service", "seb.common", "seb.config", "seb.auth"
})
@MapperScan("seb.mapper")
public class SmartEduBackendApplication {
	public static void main(String[] args) {
		SpringApplication.run(SmartEduBackendApplication.class, args);
	}

}
