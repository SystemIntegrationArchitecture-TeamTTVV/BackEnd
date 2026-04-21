package edu.iuh.fit.se.messegeservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
@org.springframework.scheduling.annotation.EnableScheduling
public class MessegeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessegeServiceApplication.class, args);
    }

}
