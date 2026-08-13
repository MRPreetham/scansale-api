package com.shopinventory;

import com.shopinventory.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class ShopInventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopInventoryApplication.class, args);
    }
}