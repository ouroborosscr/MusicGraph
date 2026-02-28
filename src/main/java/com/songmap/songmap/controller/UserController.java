package com.songmap.songmap.controller;

import com.songmap.songmap.entity.User;
import com.songmap.songmap.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 1. 添加/更新 Cookie
     */
    @PostMapping("/cookie")
    public String updateCookie(@RequestParam String username, @RequestParam String cookie) {
        // 【修复】处理 Optional 类型
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + username));
        
        user.setQqCookie(cookie);
        userRepository.save(user);
        return "Cookie 更新成功";
    }

    /**
     * 2. 查询 Cookie
     */
    @GetMapping("/cookie")
    public String getCookie(@RequestParam String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + username));
        return user.getQqCookie();
    }

    /**
     * 3. 删除 Cookie
     */
    @DeleteMapping("/cookie")
    public String deleteCookie(@RequestParam String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + username));
        user.setQqCookie(null);
        userRepository.save(user);
        return "Cookie 已删除";
    }
}