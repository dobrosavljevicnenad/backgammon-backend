package com.example.backend;

import com.example.backend.dto.response.UserResponse;
import com.example.backend.entity.User;
import com.example.backend.enums.Role;
import com.example.backend.jwt.JWTUtil;
import com.example.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.format_sql=false",
        "jwt.secret=leaderboard-integration-test-signing-key-not-for-production",
        "app.cors.allowed-origins=http://localhost:4200"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "LEADERBOARD_TEST_MYSQL_URL", matches = ".+")
class LeaderboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JWTUtil jwtUtil;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User leader;
    private User firstTie;
    private User secondTie;
    private User newcomer;
    private User zeroRating;
    private User inactive;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        String url = System.getenv("LEADERBOARD_TEST_MYSQL_URL");
        if (url == null || !url.matches("jdbc:mysql://[^/]+/backgammon_leaderboard_test_[a-z0-9_]+(?:\\?.*)?")) {
            throw new IllegalArgumentException("Leaderboard integration tests require an isolated backgammon_leaderboard_test_* database");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv("LEADERBOARD_TEST_MYSQL_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("LEADERBOARD_TEST_MYSQL_PASSWORD"));
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        leader = createUser("leader", 1500, Role.USER, true);
        firstTie = createUser("first_tie", 1200, Role.USER, true);
        secondTie = createUser("second_tie", 1200, Role.ADMIN, true);
        newcomer = createUser("newcomer", 1000, Role.USER, true);
        zeroRating = createUser("zero_rating", 0, Role.USER, true);
        inactive = createUser("inactive", 5000, Role.ADMIN, false);
    }

    @Test
    void repositoryFiltersAndOrdersPlayersBeforeApplyingPagination() {
        var firstPage = userRepository.findLeaderboard(PageRequest.of(0, 2));
        var secondPage = userRepository.findLeaderboard(PageRequest.of(1, 2));

        assertEquals(List.of(leader.getId(), firstTie.getId()),
                firstPage.getContent().stream().map(player -> player.userId()).toList());
        assertEquals(List.of(secondTie.getId(), newcomer.getId()),
                secondPage.getContent().stream().map(player -> player.userId()).toList());
        assertEquals(5, firstPage.getTotalElements());
        assertEquals(3, firstPage.getTotalPages());
        assertTrue(userRepository.findLeaderboardPlayer(inactive.getId()).isEmpty());
        assertTrue(userRepository.findLeaderboardPlayer(Long.MAX_VALUE).isEmpty());
    }

    @Test
    void countQueryMatchesEveryGlobalPositionIncludingTiesAndZeroRating() {
        var ordered = List.of(leader, firstTie, secondTie, newcomer, zeroRating);
        for (int i = 0; i < ordered.size(); i++) {
            User user = ordered.get(i);
            assertEquals(i, userRepository.countPlayersAhead(user.getRating(), user.getId()));
        }
    }

    @Test
    void jwtProtectedHttpApiReturnsGlobalRanksAcrossPages() throws Exception {
        String token = tokenFor(leader);

        mockMvc.perform(get("/api/leaderboard").param("size", "2").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].userId").value(leader.getId()))
                .andExpect(jsonPath("$.entries[0].rank").value(1))
                .andExpect(jsonPath("$.entries[1].userId").value(firstTie.getId()))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get("/api/leaderboard").param("page", "1").param("size", "2")
                        .param("sort", "rating,asc").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].userId").value(secondTie.getId()))
                .andExpect(jsonPath("$.entries[0].rank").value(3))
                .andExpect(jsonPath("$.entries[1].userId").value(newcomer.getId()))
                .andExpect(jsonPath("$.entries[1].rank").value(4));

        mockMvc.perform(get("/api/leaderboard").param("page", "2").param("size", "2")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].rank").value(5))
                .andExpect(jsonPath("$.entries[0].rating").value(0));

        mockMvc.perform(get("/api/leaderboard").param("page", "3").param("size", "2")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void meUsesRealJwtIdentityAndIncludesAdminsWithoutGames() throws Exception {
        mockMvc.perform(get("/api/leaderboard/me").param("userId", leader.getId().toString())
                        .header("Authorization", tokenFor(secondTie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(3))
                .andExpect(jsonPath("$.userId").value(secondTie.getId()))
                .andExpect(jsonPath("$.username").value("second_tie"))
                .andExpect(jsonPath("$.rating").value(1200));
    }

    @Test
    void committedRatingAndActiveChangesAreVisibleOnNextRequest() throws Exception {
        String token = tokenFor(newcomer);
        mockMvc.perform(get("/api/leaderboard/me").header("Authorization", token))
                .andExpect(jsonPath("$.rank").value(4));

        newcomer.setRating(1700);
        userRepository.saveAndFlush(newcomer);

        mockMvc.perform(get("/api/leaderboard").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.entries[0].userId").value(newcomer.getId()))
                .andExpect(jsonPath("$.entries[0].rating").value(1700));
        mockMvc.perform(get("/api/leaderboard/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.rating").value(1700));

        inactive.setActive(true);
        userRepository.saveAndFlush(inactive);

        mockMvc.perform(get("/api/leaderboard/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(2));
        mockMvc.perform(get("/api/leaderboard").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    void validTokenForInactiveAccountIsRejected() throws Exception {
        mockMvc.perform(get("/api/leaderboard").header("Authorization", tokenFor(inactive)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/leaderboard/me").header("Authorization", tokenFor(inactive)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void singleEligiblePlayerAndEmptyRepositoryHaveCorrectTotals() throws Exception {
        for (User user : List.of(firstTie, secondTie, newcomer, zeroRating)) {
            user.setActive(false);
            userRepository.saveAndFlush(user);
        }
        mockMvc.perform(get("/api/leaderboard").header("Authorization", tokenFor(leader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        leader.setActive(false);
        userRepository.saveAndFlush(leader);
        var emptyPage = userRepository.findLeaderboard(PageRequest.of(0, 20));
        assertTrue(emptyPage.isEmpty());
        assertEquals(0, emptyPage.getTotalElements());
        assertEquals(0, emptyPage.getTotalPages());
    }

    @Test
    void readingDoesNotChangePersistedUserData() throws Exception {
        var before = userRepository.findAll().stream().map(UserResponse::from).toList();

        mockMvc.perform(get("/api/leaderboard").header("Authorization", tokenFor(leader)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/leaderboard/me").header("Authorization", tokenFor(leader)))
                .andExpect(status().isOk());

        assertEquals(before, userRepository.findAll().stream().map(UserResponse::from).toList());
    }

    @Test
    void schemaContainsLeaderboardIndex() {
        var columns = jdbcTemplate.queryForList("""
                SELECT COLUMN_NAME
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'users'
                  AND INDEX_NAME = 'idx_users_leaderboard'
                ORDER BY SEQ_IN_INDEX
                """, String.class);

        assertEquals(List.of("active", "rating", "id"), columns);
    }

    private User createUser(String username, int rating, Role role, boolean active) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPasswordHash("unused-test-password-hash");
        user.setRating(rating);
        user.setRole(role);
        user.setActive(active);
        return userRepository.saveAndFlush(user);
    }

    private String tokenFor(User user) {
        return "Bearer " + jwtUtil.generateToken(user.getUsername(), user.getRole().name());
    }
}
