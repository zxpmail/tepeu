package com.tepeu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TepeuApplication {

    public static void main(String[] args) {
        SpringApplication.run(TepeuApplication.class, args);
    }
}
