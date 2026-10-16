package com.example.backend.service;

import com.example.backend.entity.User;
import com.example.backend.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Logged-in user taken from the JWT (set by JWTFilter).
 * Controllers use this instead of accepting a userId from the client.
 */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !(authentication.getPrincipal() instanceof UserDetails principal)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Not authenticated"
            );
        }

        return userRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User not found"
                ));
    }

    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }
}
