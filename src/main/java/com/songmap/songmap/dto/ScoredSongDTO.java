package com.songmap.songmap.dto;

import com.songmap.songmap.entity.Song;
import lombok.Data;

@Data
public class ScoredSongDTO implements Comparable<ScoredSongDTO> {
    private Song song;
    private double score;
    
    // 调试信息：让你知道为什么这首歌排第一
    // 例如: "Dir:1.0 * Fresh:0.9 * (Edge:5.0 + Node:1.0)"
    private String reason; 

    @Override
    public int compareTo(ScoredSongDTO o) {
        // 降序排列 (分数高的在前)
        return Double.compare(o.score, this.score);
    }
}