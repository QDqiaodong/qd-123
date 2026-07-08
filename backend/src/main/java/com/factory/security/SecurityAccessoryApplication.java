package com.factory.security;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.factory.security.mapper")
public class SecurityAccessoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityAccessoryApplication.class, args);
    }
}
