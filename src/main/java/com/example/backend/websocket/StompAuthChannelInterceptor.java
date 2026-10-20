package com.example.backend.websocket;

import com.example.backend.entity.User;
import com.example.backend.jwt.JWTUtil;
import com.example.backend.repository.GamePlayerRepository;
import com.example.backend.repository.UserRepository;

import io.jsonwebtoken.JwtException;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates STOMP {@code CONNECT} frames using the same JWT scheme as
 * the REST API (Authorization: Bearer ...), and authorizes
 * {@code SUBSCRIBE} frames so a user can only listen to
 * {@code /topic/games/{gameId}} for a game they are actually playing in.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern GAME_TOPIC = Pattern.compile("^/topic/games/(\\d+)$");

    private final JWTUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final GamePlayerRepository gamePlayerRepository;

    public StompAuthChannelInterceptor(
            JWTUtil jwtUtil,
            UserDetailsService userDetailsService,
            UserRepository userRepository,
            GamePlayerRepository gamePlayerRepository
    ) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.gamePlayerRepository = gamePlayerRepository;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Principal user = authenticate(accessor);
            accessor.setUser(user);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }

        return message;
    }

    private Principal authenticate(StompHeaderAccessor accessor) {
        String authHeader = firstNativeHeader(accessor, "Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new MessagingException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            String username = jwtUtil.extractUsername(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (!userDetails.isEnabled() || !jwtUtil.validateToken(token, userDetails)) {
                throw new MessagingException("Invalid or expired token");
            }

            return new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
        } catch (JwtException | IllegalArgumentException e) {
            throw new MessagingException("Invalid token", e);
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        Matcher matcher = GAME_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            // Not a per-game topic; nothing to authorize here.
            return;
        }

        Principal principal = accessor.getUser();
        if (principal == null) {
            throw new MessagingException("Not authenticated");
        }

        Long gameId = Long.valueOf(matcher.group(1));
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new MessagingException("User not found"));

        if (!gamePlayerRepository.existsByGameIdAndUserId(gameId, user.getId())) {
            throw new MessagingException("Not a player in game " + gameId);
        }
    }

    private String firstNativeHeader(StompHeaderAccessor accessor, String name) {
        var values = accessor.getNativeHeader(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }
}
