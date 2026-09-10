package com.factory.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Mapper 接口均已标注 @Mapper，由 MyBatis 自动扫描注册；
// 不使用 @MapperScan，避免 @WebMvcTest 等切片测试加载 Mapper 时因缺少数据源而失败
@SpringBootApplication
public class SecurityAccessoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityAccessoryApplication.class, args);
    }
}
