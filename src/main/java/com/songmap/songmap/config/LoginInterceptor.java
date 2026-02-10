package com.songmap.songmap.config;

import com.songmap.songmap.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    public LoginInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 OPTIONS 请求 (跨域预检)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 1. 从 Header 拿 Token
        String token = request.getHeader("Authorization");
        
        // 2. 校验 Token
        if (token != null && !token.isEmpty()) {
            Long userId = authService.getUserIdByToken(token);
            if (userId != null) {
                // 3. 将 userId 放入 Request 属性，方便 Controller 使用
                request.setAttribute("currentUserId", userId);
                return true; // 放行
            }
        }

        // 4. 失败：返回 401
        response.setStatus(401);
        response.getWriter().write("Unauthorized: Please login first");
        return false;
    }
}