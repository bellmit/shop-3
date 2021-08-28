package com.zhang.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ShopApplication {

    public static void main(String[] args) {
        System.out.println( "Hello World!" );
        SpringApplication.run(ShopApplication.class, args);
    }

}