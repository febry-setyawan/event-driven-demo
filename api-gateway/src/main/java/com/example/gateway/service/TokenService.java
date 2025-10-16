package com.example.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class TokenService {
    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Value("${jwt.expiration:86400000}")
    private Long expiration;

    public void storeToken(String username, String token) {
        String key = "jwt:" + username;
        redisTemplate.opsForValue().set(key, token, expiration, TimeUnit.MILLISECONDS);
        logger.info("Token stored in Redis for user: {}", username);
    }

    public boolean validateToken(String username, String token) {
        String key = "jwt:" + username;
        String storedToken = redisTemplate.opsForValue().get(key);
        return token.equals(storedToken);
    }

    public void revokeToken(String username) {
        String key = "jwt:" + username;
        redisTemplate.delete(key);
        logger.info("Token revoked for user: {}", username);
    }
}
