package com.example.ordergateway.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class TokenService {

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    public Mono<Void> storeToken(String username, String token) {
        return redisTemplate.opsForValue()
            .set("jwt:" + username, token, Duration.ofHours(24))
            .then();
    }

    public Mono<Boolean> isTokenValid(String username, String token) {
        return redisTemplate.opsForValue()
            .get("jwt:" + username)
            .map(storedToken -> storedToken.equals(token))
            .defaultIfEmpty(false);
    }
}
