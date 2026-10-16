package com.example.backend.request;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LogInRequest {

    @NotNull
    @Email
    private String email;
    @NotNull
    private String password;

}