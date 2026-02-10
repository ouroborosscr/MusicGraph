package com.songmap.songmap.controller;

import com.songmap.songmap.entity.User;
import com.songmap.songmap.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public User register(@RequestParam String username, @RequestParam String password) {
        return authService.register(username, password);
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestParam String username, @RequestParam String password) {
        String token = authService.login(username, password);
        // 返回 JSON: { "token": "xxxx-xxxx", "username": "alice" }
        return Map.of("token", token, "username", username);
    }
    
    @PostMapping("/logout")
    public String logout(@RequestHeader("Authorization") String token) {
        authService.logout(token);
        return "Logged out";
    }
}