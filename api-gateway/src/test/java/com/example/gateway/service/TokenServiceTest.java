package com.example.gateway.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenService tokenService;

    private static final Long TEST_EXPIRATION = 3600000L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tokenService, "expiration", TEST_EXPIRATION);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ===================== POSITIVE SCENARIOS =====================

    @Test
    void testStoreToken_Success() {
        // Given
        String username = "testuser";
        String token = "test.jwt.token";

        // When
        tokenService.storeToken(username, token);

        // Then
        verify(valueOperations, times(1)).set(
            eq("jwt:testuser"), 
            eq(token), 
            eq(TEST_EXPIRATION), 
            eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    void testValidateToken_ValidToken_ReturnsTrue() {
        // Given
        String username = "testuser";
        String token = "test.jwt.token";
        when(valueOperations.get("jwt:testuser")).thenReturn(token);

        // When
        boolean result = tokenService.validateToken(username, token);

        // Then
        assertTrue(result);
        verify(valueOperations, times(1)).get("jwt:testuser");
    }

    @Test
    void testRevokeToken_Success() {
        // Given
        String username = "testuser";

        // When
        tokenService.revokeToken(username);

        // Then
        verify(redisTemplate, times(1)).delete("jwt:testuser");
    }

    @Test
    void testStoreToken_MultipleUsers() {
        // Given
        String[] usernames = {"user1", "user2", "user3"};
        String[] tokens = {"token1", "token2", "token3"};

        for (int i = 0; i < usernames.length; i++) {
            // When
            tokenService.storeToken(usernames[i], tokens[i]);

            // Then
            verify(valueOperations, times(1)).set(
                eq("jwt:" + usernames[i]), 
                eq(tokens[i]), 
                eq(TEST_EXPIRATION), 
                eq(TimeUnit.MILLISECONDS)
            );
        }
    }

    @Test
    void testStoreToken_SameUserDifferentTokens_Overwrites() {
        // Given
        String username = "testuser";
        String token1 = "old.token";
        String token2 = "new.token";

        // When
        tokenService.storeToken(username, token1);
        tokenService.storeToken(username, token2);

        // Then
        verify(valueOperations, times(2)).set(
            eq("jwt:testuser"), 
            anyString(), 
            eq(TEST_EXPIRATION), 
            eq(TimeUnit.MILLISECONDS)
        );
    }

    // ===================== NEGATIVE SCENARIOS =====================

    @Test
    void testValidateToken_InvalidToken_ReturnsFalse() {
        // Given
        String username = "testuser";
        String storedToken = "stored.token";
        String providedToken = "different.token";
        when(valueOperations.get("jwt:testuser")).thenReturn(storedToken);

        // When
        boolean result = tokenService.validateToken(username, providedToken);

        // Then
        assertFalse(result);
    }

    @Test
    void testValidateToken_NoStoredToken_ReturnsFalse() {
        // Given
        String username = "testuser";
        String token = "test.token";
        when(valueOperations.get("jwt:testuser")).thenReturn(null);

        // When
        boolean result = tokenService.validateToken(username, token);

        // Then
        assertFalse(result);
    }

    @Test
    void testValidateToken_NullProvidedToken_ThrowsNPE() {
        // Given
        String username = "testuser";
        when(valueOperations.get("jwt:testuser")).thenReturn("stored.token");

        // When/Then - token.equals(storedToken) throws NPE when token is null
        assertThrows(NullPointerException.class, () -> tokenService.validateToken(username, null));
    }

    @Test
    void testValidateToken_BothNull_ThrowsNPE() {
        // Given
        String username = "testuser";
        when(valueOperations.get("jwt:testuser")).thenReturn(null);

        // When/Then - token.equals(storedToken) throws NPE when token is null
        assertThrows(NullPointerException.class, () -> tokenService.validateToken(username, null));
    }

    @Test
    void testStoreToken_NullUsername_HandlesGracefully() {
        // Given
        String token = "test.token";

        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> tokenService.storeToken(null, token));
        verify(valueOperations, times(1)).set(
            eq("jwt:null"), 
            eq(token), 
            eq(TEST_EXPIRATION), 
            eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    void testStoreToken_NullToken_HandlesGracefully() {
        // Given
        String username = "testuser";

        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> tokenService.storeToken(username, null));
        verify(valueOperations, times(1)).set(
            eq("jwt:testuser"), 
            eq(null), 
            eq(TEST_EXPIRATION), 
            eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    void testRevokeToken_NonExistentToken_HandlesGracefully() {
        // Given
        String username = "nonexistent";
        when(redisTemplate.delete("jwt:nonexistent")).thenReturn(false);

        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> tokenService.revokeToken(username));
        verify(redisTemplate, times(1)).delete("jwt:nonexistent");
    }

    @Test
    void testRevokeToken_NullUsername_HandlesGracefully() {
        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> tokenService.revokeToken(null));
        verify(redisTemplate, times(1)).delete("jwt:null");
    }

    @Test
    void testValidateToken_EmptyStrings_HandlesCorrectly() {
        // Given
        String username = "";
        String token = "";
        when(valueOperations.get("jwt:")).thenReturn("");

        // When
        boolean result = tokenService.validateToken(username, token);

        // Then
        assertTrue(result);
    }

    @Test
    void testStoreToken_RedisException_PropagatesException() {
        // Given
        String username = "testuser";
        String token = "test.token";
        doThrow(new RuntimeException("Redis connection error"))
            .when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        // When/Then
        assertThrows(RuntimeException.class, () -> tokenService.storeToken(username, token));
    }

    @Test
    void testValidateToken_RedisException_PropagatesException() {
        // Given
        String username = "testuser";
        String token = "test.token";
        when(valueOperations.get(anyString()))
            .thenThrow(new RuntimeException("Redis connection error"));

        // When/Then
        assertThrows(RuntimeException.class, () -> tokenService.validateToken(username, token));
    }

    @Test
    void testRevokeToken_RedisException_PropagatesException() {
        // Given
        String username = "testuser";
        when(redisTemplate.delete(anyString()))
            .thenThrow(new RuntimeException("Redis connection error"));

        // When/Then
        assertThrows(RuntimeException.class, () -> tokenService.revokeToken(username));
    }
}
