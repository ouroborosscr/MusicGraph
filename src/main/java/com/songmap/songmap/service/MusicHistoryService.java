package com.songmap.songmap.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MusicHistoryService {

    private final StringRedisTemplate redisTemplate;
    private static final String SEPARATOR = "::";

    public MusicHistoryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String getHistoryKey(Long graphId) {
        // 【关键】将 Key 加上 graphId 后缀，实现历史记录隔离
        return "history:graph:" + (graphId == null ? "global" : graphId);
    }

    // Lua 脚本保持不变
    private static final String LUA_SCRIPT_LRU = 
            "redis.call('LREM', KEYS[1], 0, ARGV[1]); " +
            "redis.call('LPUSH', KEYS[1], ARGV[1]); " +
            "redis.call('LTRIM', KEYS[1], 0, ARGV[2]); " +
            "return 1;";

    private static final DefaultRedisScript<Long> REDIS_SCRIPT = 
            new DefaultRedisScript<>(LUA_SCRIPT_LRU, Long.class);

    public void updateHistory(Long graphId, Long songId, String songName, int limit) {
        if (songId == null) return;
        String key = getHistoryKey(graphId);
        String entry = songId + SEPARATOR + songName;
        String limitStr = String.valueOf(limit - 1);

        redisTemplate.execute(REDIS_SCRIPT, Collections.singletonList(key), entry, limitStr);
    }

    public Long getLastListenedSongId(Long graphId) {
        String key = getHistoryKey(graphId);
        String entry = redisTemplate.opsForList().index(key, 0);
        if (entry == null) return null;
        try {
            return Long.valueOf(entry.split(SEPARATOR)[0]);
        } catch (Exception e) {
            return null;
        }
    }

    public List<Map<String, String>> getStructuredHistory(Long graphId) {
        String key = getHistoryKey(graphId);
        List<String> rawList = redisTemplate.opsForList().range(key, 0, -1);
        List<Map<String, String>> result = new ArrayList<>();
        if (rawList != null) {
            for (String entry : rawList) {
                String[] parts = entry.split(SEPARATOR);
                if (parts.length >= 2) {
                    Map<String, String> map = new HashMap<>();
                    map.put("id", parts[0]);
                    map.put("name", parts[1]);
                    result.add(map);
                }
            }
        }
        return result;
    }

    public List<String> getHistory(Long graphId) {
        return redisTemplate.opsForList().range(getHistoryKey(graphId), 0, -1);
    }
}