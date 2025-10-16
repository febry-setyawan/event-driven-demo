package com.example.gateway.controller;

import com.example.gateway.dto.AuthRequest;
import com.example.gateway.dto.AuthResponse;
import com.example.gateway.security.JwtUtil;
import com.example.gateway.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Authentication endpoints")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate user and get JWT token")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        logger.info("Login attempt for user: {}", request.getUsername());
        try {
            var userDetails = userDetailsService.loadUserByUsername(request.getUsername());
            if (passwordEncoder.matches(request.getPassword(), userDetails.getPassword())) {
                String token = jwtUtil.generateToken(request.getUsername());
                tokenService.storeToken(request.getUsername(), token);
                logger.info("Login successful for user: {}", request.getUsername());
                return ResponseEntity.ok(new AuthResponse(token));
            }
            throw new BadCredentialsException("Invalid credentials");
        } catch (Exception e) {
            logger.error("Login failed for user: {}", request.getUsername());
            return ResponseEntity.status(401).build();
        }
    }
}
