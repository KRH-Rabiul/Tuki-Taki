package com.marketbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// This is the entry point of the whole project.
// Run this file (or "mvn spring-boot:run") to start the website.
@SpringBootApplication
public class MarketBridgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarketBridgeApplication.class, args);
    }
}
