package com.hbsd.labselect;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.hbsd.labselect.mapper")
@SpringBootApplication
public class LabSelectApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabSelectApplication.class, args);
    }
}
