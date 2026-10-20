package com.example.backend.websocket;

import com.example.backend.entity.User;
import com.example.backend.jwt.JWTUtil;
import com.example.backend.repository.GamePlayerRepository;
import com.example.backend.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock
    private JWTUtil jwtUtil;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GamePlayerRepository gamePlayerRepository;

    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(
                jwtUtil, userDetailsService, userRepository, gamePlayerRepository
        );
    }

    private UserDetails userDetails(String username) {
        return org.springframework.security.core.userdetails.User
                .withUsername(username)
                .password("irrelevant")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
    }

    private Message<byte[]> connectMessage(String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authHeader != null) {
            accessor.addNativeHeader("Authorization", authHeader);
        }
        accessor.setLeaveMutable(true);
        return org.springframework.messaging.support.MessageBuilder
                .createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeMessage(String destination, java.security.Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(principal);
        accessor.setLeaveMutable(true);
        return org.springframework.messaging.support.MessageBuilder
                .createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void connectRejectedWithoutAuthorizationHeader() {
        assertThrows(MessagingException.class, () -> interceptor.preSend(connectMessage(null), null));
    }

    @Test
    void connectRejectedWithInvalidToken() {
        when(jwtUtil.extractUsername("bad-token")).thenThrow(new io.jsonwebtoken.JwtException("bad"));

        assertThrows(MessagingException.class,
                () -> interceptor.preSend(connectMessage("Bearer bad-token"), null));
    }

    @Test
    void connectAcceptedAndSetsPrincipalForValidToken() {
        UserDetails details = userDetails("alice");
        when(jwtUtil.extractUsername("good-token")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(details);
        when(jwtUtil.validateToken("good-token", details)).thenReturn(true);

        Message<?> result = interceptor.preSend(connectMessage("Bearer good-token"), null);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertEquals("alice", resultAccessor.getUser().getName());
    }

    @Test
    void subscribeRejectedWhenNotAuthenticated() {
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(subscribeMessage("/topic/games/1", null), null));
    }

    @Test
    void subscribeRejectedWhenNotAPlayerInGame() {
        var principal = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                userDetails("bob"), null, List.of());
        User bob = new User();
        bob.setId(5L);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(gamePlayerRepository.existsByGameIdAndUserId(1L, 5L)).thenReturn(false);

        assertThrows(MessagingException.class,
                () -> interceptor.preSend(subscribeMessage("/topic/games/1", principal), null));
    }

    @Test
    void subscribeAllowedWhenPlayerInGame() {
        var principal = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                userDetails("bob"), null, List.of());
        User bob = new User();
        bob.setId(5L);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(gamePlayerRepository.existsByGameIdAndUserId(1L, 5L)).thenReturn(true);

        Message<?> result = interceptor.preSend(subscribeMessage("/topic/games/1", principal), null);

        assertEquals(SimpMessageType.SUBSCRIBE, StompHeaderAccessor.wrap(result).getMessageType());
    }

    @Test
    void subscribeToUnrelatedDestinationSkipsAuthorization() {
        Message<?> result = interceptor.preSend(subscribeMessage("/topic/lobby", null), null);

        assertEquals(SimpMessageType.SUBSCRIBE, StompHeaderAccessor.wrap(result).getMessageType());
    }
}
