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

    // 固定的 Redis Key 前缀
    private static final String HISTORY_KEY = "history:global"; 
    // 你原来的代码里有 userId，但你的 Controller 目前没传 userId，
    // 为了让代码跑通，我们先用全局 Key。如果你后续加用户系统，把这里改成动态 Key 即可。

    private static final String SEPARATOR = "::"; // 分隔符

    public MusicHistoryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Lua 脚本：移除旧的 -> 插入头部 -> 修剪长度
    private static final String LUA_SCRIPT_LRU = 
            "redis.call('LREM', KEYS[1], 0, ARGV[1]); " +
            "redis.call('LPUSH', KEYS[1], ARGV[1]); " +
            "redis.call('LTRIM', KEYS[1], 0, ARGV[2]); " +
            "return 1;";

    private static final DefaultRedisScript<Long> REDIS_SCRIPT = 
            new DefaultRedisScript<>(LUA_SCRIPT_LRU, Long.class);

    /**
     * 更新播放历史 (LRU)
     * @param songId 歌曲ID (存 Long 转 String)
     * @param limit  历史记录最大长度
     */
    public void updateHistory(Long songId, String songName, int limit) {
        if (songId == null) return;
        
        // 打包： "10::七里香"
        String entry = songId + SEPARATOR + songName;
        String limitStr = String.valueOf(limit - 1);

        redisTemplate.execute(
                REDIS_SCRIPT,
                Collections.singletonList(HISTORY_KEY),
                entry, // 存入的是打包后的字符串
                limitStr
        );
    }

    /**
     * 【新增关键方法】获取最近一次听的歌曲 ID（不修改列表）
     * 对应 Redis 命令：LINDEX key 0
     */
    public Long getLastListenedSongId() {
        String entry = redisTemplate.opsForList().index(HISTORY_KEY, 0);
        if (entry == null) return null;
        
        // 解包：取分隔符前面的部分
        try {
            String[] parts = entry.split(SEPARATOR);
            return Long.valueOf(parts[0]);
        } catch (Exception e) {
            return null; // 防止脏数据报错
        }
    }

    /**
     * 【新增】获取结构化的历史列表（给前端用）
     * 返回格式：[{"id": "10", "name": "七里香"}, ...]
     */
    public List<Map<String, String>> getStructuredHistory() {
        List<String> rawList = redisTemplate.opsForList().range(HISTORY_KEY, 0, -1);
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

    /**
     * 获取完整历史列表
     */
    public List<String> getHistory() {
        return redisTemplate.opsForList().range(HISTORY_KEY, 0, -1);
    }
}