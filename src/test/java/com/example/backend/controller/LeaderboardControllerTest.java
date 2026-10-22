package com.example.backend.controller;

import com.example.backend.entity.User;
import com.example.backend.exception.GlobalExceptionHandler;
import com.example.backend.jwt.JWTFilter;
import com.example.backend.jwt.JWTUtil;
import com.example.backend.repository.LeaderboardPlayerProjection;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.PasswordEncoderConfig;
import com.example.backend.security.SecurityConfig;
import com.example.backend.service.CurrentUserService;
import com.example.backend.service.LeaderboardService;
import com.example.backend.service.UserService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = LeaderboardController.class, properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({
        SecurityConfig.class, JWTFilter.class, PasswordEncoderConfig.class,
        LeaderboardService.class, CurrentUserService.class, GlobalExceptionHandler.class
})
class LeaderboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private JWTUtil jwtUtil;

    @Test
    @WithMockUser
    void returnsOnlyLeaderboardFieldsWithDefaultPaginationAndNoStore() throws Exception {
        var pageable = PageRequest.of(0, 20);
        when(userRepository.findLeaderboard(pageable)).thenReturn(new PageImpl<>(
                List.of(new LeaderboardPlayerProjection(42L, "leader", 1250)), pageable, 1));

        mockMvc.perform(get("/api/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.entries[0]", aMapWithSize(4)))
                .andExpect(jsonPath("$.entries[0].rank").value(1))
                .andExpect(jsonPath("$.entries[0].userId").value(42))
                .andExpect(jsonPath("$.entries[0].username").value("leader"))
                .andExpect(jsonPath("$.entries[0].rating").value(1250));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    @WithMockUser
    void acceptsExplicitPaginationBoundaries(int size) throws Exception {
        var pageable = PageRequest.of(0, size);
        when(userRepository.findLeaderboard(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        mockMvc.perform(get("/api/leaderboard").param("page", "0").param("size", String.valueOf(size)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(size))
                .andExpect(jsonPath("$.entries").isEmpty())
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @ParameterizedTest
    @CsvSource({
            "page,-1", "size,0", "size,-1", "size,101",
            "page,text", "size,1.5", "page,2147483648", "size,999999999999999999999",
            "page,2147483647"
    })
    @WithMockUser
    void rejectsInvalidQueryParametersAsJson(String parameter, String value) throws Exception {
        mockMvc.perform(get("/api/leaderboard").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", aMapWithSize(1)))
                .andExpect(jsonPath("$.message").isString());

        verifyNoInteractions(userRepository);
    }

    @Test
    @WithMockUser(username = "alice")
    void meUsesAuthenticatedIdentityAndIgnoresClientUserId() throws Exception {
        User alice = new User();
        alice.setId(42L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findLeaderboardPlayer(42L))
                .thenReturn(Optional.of(new LeaderboardPlayerProjection(42L, "alice", 1200)));
        when(userRepository.countPlayersAhead(1200, 42L)).thenReturn(9L);

        mockMvc.perform(get("/api/leaderboard/me").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.rank").value(10))
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.rating").value(1200));
    }

    @Test
    @WithMockUser(username = "alice")
    void meReturns404IfPlayerBecomesUnrankedAfterAuthentication() throws Exception {
        User alice = new User();
        alice.setId(42L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findLeaderboardPlayer(42L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/leaderboard/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User is not ranked"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/leaderboard", "/api/leaderboard/me"})
    void rejectsUnauthenticatedRequests(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(userRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/leaderboard", "/api/leaderboard/me"})
    void rejectsInvalidJwt(String path) throws Exception {
        when(jwtUtil.extractUsername("invalid")).thenThrow(new JwtException("Invalid token"));

        mockMvc.perform(get(path).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/leaderboard", "/api/leaderboard/me"})
    void rejectsExpiredJwt(String path) throws Exception {
        when(jwtUtil.extractUsername("expired")).thenThrow(new ExpiredJwtException(null, null, "Expired token"));

        mockMvc.perform(get(path).header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userRepository);
    }

    @Test
    void preservesAngularCorsConfiguration() throws Exception {
        mockMvc.perform(get("/api/leaderboard").header("Origin", "http://localhost:4200"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Vary", containsString("Origin")));
    }
}
