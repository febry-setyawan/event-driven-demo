package com.example.ordergateway.config;

import com.example.ordergateway.dto.AuthRequest;
import com.example.ordergateway.dto.AuthResponse;
import com.example.ordergateway.security.JwtUtil;
import com.example.ordergateway.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TokenService tokenService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@RequestBody AuthRequest request) {
        logger.info("Login attempt for user: {}", request.getUsername());
        
        if ("admin".equals(request.getUsername()) && "admin".equals(request.getPassword())) {
            String token = jwtUtil.generateToken(request.getUsername());
            return tokenService.storeToken(request.getUsername(), token)
                .then(Mono.fromCallable(() -> {
                    logger.info("Login successful for user: {}", request.getUsername());
                    return ResponseEntity.ok(new AuthResponse(token));
                }));
        }
        
        logger.error("Login failed for user: {}", request.getUsername());
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
