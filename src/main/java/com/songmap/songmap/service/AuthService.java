package com.songmap.songmap.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.songmap.songmap.entity.User;
import com.songmap.songmap.repository.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    public AuthService(UserRepository userRepository, StringRedisTemplate redisTemplate) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }

    // 注册
    public User register(String username, String rawPassword) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        // BCrypt 加密
        user.setPassword(BCrypt.hashpw(rawPassword));
        user.setAvatar("https://api.dicebear.com/7.x/avataaars/svg?seed=" + username); // 随机头像
        return userRepository.save(user);
    }

    // 登录：成功返回 Token
    public String login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (!BCrypt.checkpw(rawPassword, user.getPassword())) {
            throw new IllegalArgumentException("密码错误");
        }

        // 生成 Token (UUID)
        String token = IdUtil.simpleUUID();
        
        // 存入 Redis: key=token, value=userId
        // 既然你要永久，就不设置 expire，或者设置个 3650 天
        redisTemplate.opsForValue().set("login:token:" + token, user.getId().toString());
        
        return token;
    }

    // 根据 Token 获取用户ID
    public Long getUserIdByToken(String token) {
        String userIdStr = redisTemplate.opsForValue().get("login:token:" + token);
        return userIdStr == null ? null : Long.valueOf(userIdStr);
    }
    
    // 登出
    public void logout(String token) {
        redisTemplate.delete("login:token:" + token);
    }
}