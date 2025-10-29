package com.example.gateway.security;

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
    void testGenerateToken_SameUsername_GeneratesDifferentTokens() throws InterruptedException {
        // Given
        String username = "testuser";
        
        // When
        String token1 = jwtUtil.generateToken(username);
        Thread.sleep(1000); // Increased delay to ensure different timestamps
        String token2 = jwtUtil.generateToken(username);

        // Then
        assertNotEquals(token1, token2); // Different because of different timestamps
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

    @Test
    void testGenerateToken_WithSpecialCharacters_Success() {
        // Given
        String username = "user@example.com";

        // When
        String token = jwtUtil.generateToken(username);
        String extracted = jwtUtil.extractUsername(token);

        // Then
        assertNotNull(token);
        assertEquals(username, extracted);
    }

    @Test
    void testGenerateToken_WithNumbers_Success() {
        // Given
        String username = "user123";

        // When
        String token = jwtUtil.generateToken(username);
        String extracted = jwtUtil.extractUsername(token);

        // Then
        assertEquals(username, extracted);
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
    void testValidateToken_MalformedToken_ReturnsFalse() {
        // Given
        String malformedToken = "notavalidjwt";

        // When
        boolean isValid = jwtUtil.validateToken(malformedToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testValidateToken_EmptyToken_ReturnsFalse() {
        // Given
        String emptyToken = "";

        // When
        boolean isValid = jwtUtil.validateToken(emptyToken);

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
    void testExtractUsername_MalformedToken_ThrowsException() {
        // Given
        String malformedToken = "notavalidjwt";

        // When/Then
        assertThrows(Exception.class, () -> jwtUtil.extractUsername(malformedToken));
    }

    @Test
    void testValidateToken_TokenFromDifferentSecret_ReturnsFalse() {
        // Given - Create a token with different secret
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
        // Given - Create JwtUtil with very short expiration
        JwtUtil shortExpirationJwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(shortExpirationJwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(shortExpirationJwtUtil, "expiration", 1L); // 1 millisecond
        
        String token = shortExpirationJwtUtil.generateToken("testuser");
        Thread.sleep(100); // Wait for token to expire

        // When
        boolean isValid = shortExpirationJwtUtil.validateToken(token);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testGenerateToken_EmptyUsername_GeneratesToken() {
        // Given
        String emptyUsername = "";

        // When
        String token = jwtUtil.generateToken(emptyUsername);

        // Then
        assertNotNull(token);
        String extracted = jwtUtil.extractUsername(token);
        // Empty string in JWT subject is stored as null
        assertTrue(extracted == null || extracted.isEmpty());
    }

    @Test
    void testGenerateToken_NullUsername_GeneratesToken() {
        // When
        String token = jwtUtil.generateToken(null);

        // Then
        assertNotNull(token);
        String extracted = jwtUtil.extractUsername(token);
        assertNull(extracted);
    }

    @Test
    void testValidateToken_ModifiedToken_ReturnsFalse() {
        // Given
        String originalToken = jwtUtil.generateToken("testuser");
        String modifiedToken = originalToken + "modified";

        // When
        boolean isValid = jwtUtil.validateToken(modifiedToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testValidateToken_PartialToken_ReturnsFalse() {
        // Given
        String originalToken = jwtUtil.generateToken("testuser");
        String partialToken = originalToken.substring(0, originalToken.length() / 2);

        // When
        boolean isValid = jwtUtil.validateToken(partialToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testGenerateToken_VeryLongUsername_Success() {
        // Given
        String longUsername = "a".repeat(1000);

        // When
        String token = jwtUtil.generateToken(longUsername);
        String extracted = jwtUtil.extractUsername(token);

        // Then
        assertNotNull(token);
        assertEquals(longUsername, extracted);
    }
}
