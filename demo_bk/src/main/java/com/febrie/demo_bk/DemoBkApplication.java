package com.febrie.demo_bk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DemoBkApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoBkApplication.class, args);
    }

}
