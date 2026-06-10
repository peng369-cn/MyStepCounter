package com.pengchangwei.stepserver;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.pengchangwei.stepserver.mapper")
public class StepServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(StepServerApplication.class, args);
    }
}
