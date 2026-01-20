package com.songmap.songmap.controller;

import com.songmap.songmap.entity.Song;
import com.songmap.songmap.service.MusicGraphService;
import org.springframework.web.bind.annotation.*;

@RestController // 声明这是一个处理 HTTP 请求的控制器
@RequestMapping("/api/music") // 所有接口前缀
public class MusicController {
    private final MusicGraphService musicService;

    public MusicController(MusicGraphService musicService) {
        this.musicService = musicService;
    }

    // 动作：听歌
    // 请求：POST /api/music/listen?name=七里香&forceNew=false
    @PostMapping("/listen")
    public Song listen(@RequestParam String name, 
                       @RequestParam(defaultValue = "false") boolean forceNew) {
        return musicService.addSong(name, forceNew);
    }
    
    // 你提到的预留接口：填充 API 数据
    // 这个后面再实现，现在先占个位
    @PostMapping("/enrich/{songId}")
    public String enrichData(@PathVariable Long songId) {
        return "TODO: Call NetEase/Tencent API for song " + songId;
    }
}
