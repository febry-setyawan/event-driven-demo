package com.example.ordergateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private static final String TEST_SECRET = "mySecretKeyForTestingPurposesWhichNeedsToBeAtLeast32Characters";
    private static final Long TEST_EXPIRATION = 3600000L; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", TEST_EXPIRATION);
    }

    // ===================== POSITIVE SCENARIOS =====================

    @Test
    void testGenerateToken_Success() {
        // When
        String token = jwtUtil.generateToken("testuser");

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.split("\\.").length == 3); // JWT has 3 parts
    }

    @Test
    void testExtractUsername_Success() {
        // Given
        String username = "testuser";
        String token = jwtUtil.generateToken(username);

        // When
        String extractedUsername = jwtUtil.extractUsername(token);

        // Then
        assertEquals(username, extractedUsername);
    }

    @Test
    void testValidateToken_ValidToken_ReturnsTrue() {
        // Given
        String token = jwtUtil.generateToken("testuser");

        // When
        boolean isValid = jwtUtil.validateToken(token);

        // Then
        assertTrue(isValid);
    }

    @Test
    void testGenerateToken_DifferentUsernames_GeneratesDifferentTokens() {
        // When
        String token1 = jwtUtil.generateToken("user1");
        String token2 = jwtUtil.generateToken("user2");

        // Then
        assertNotEquals(token1, token2);
    }

    @Test
    void testExtractUsername_MultipleUsers_ExtractsCorrectly() {
        // Given
        String[] usernames = {"user1", "user2", "admin", "customer123"};

        for (String username : usernames) {
            // When
            String token = jwtUtil.generateToken(username);
            String extracted = jwtUtil.extractUsername(token);

            // Then
            assertEquals(username, extracted);
        }
    }

    // ===================== NEGATIVE SCENARIOS =====================

    @Test
    void testValidateToken_InvalidToken_ReturnsFalse() {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        boolean isValid = jwtUtil.validateToken(invalidToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testValidateToken_NullToken_ReturnsFalse() {
        // When
        boolean isValid = jwtUtil.validateToken(null);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testExtractUsername_InvalidToken_ThrowsException() {
        // Given
        String invalidToken = "invalid.token.here";

        // When/Then
        assertThrows(Exception.class, () -> jwtUtil.extractUsername(invalidToken));
    }

    @Test
    void testValidateToken_TokenFromDifferentSecret_ReturnsFalse() {
        // Given
        JwtUtil differentJwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(differentJwtUtil, "secret", "differentSecretKeyThatIsAtLeast32CharactersLong!!");
        ReflectionTestUtils.setField(differentJwtUtil, "expiration", TEST_EXPIRATION);
        String tokenWithDifferentSecret = differentJwtUtil.generateToken("testuser");

        // When
        boolean isValid = jwtUtil.validateToken(tokenWithDifferentSecret);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testValidateToken_ExpiredToken_ReturnsFalse() throws InterruptedException {
        // Given
        JwtUtil shortExpirationJwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(shortExpirationJwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(shortExpirationJwtUtil, "expiration", 1L);
        
        String token = shortExpirationJwtUtil.generateToken("testuser");
        Thread.sleep(100);

        // When
        boolean isValid = shortExpirationJwtUtil.validateToken(token);

        // Then
        assertFalse(isValid);
    }
}
