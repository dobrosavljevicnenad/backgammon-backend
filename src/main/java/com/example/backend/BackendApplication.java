package com.example.backend;

import com.example.backend.repository.RoomRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    @Bean
    CommandLineRunner testDatabase(RoomRepository roomRepository) {
        return args -> {
            System.out.println(
                    "Number of rooms: " + roomRepository.count()
            );
        };
    }
}