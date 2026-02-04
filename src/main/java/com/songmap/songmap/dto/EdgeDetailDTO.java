package com.songmap.songmap.dto;

import com.songmap.songmap.entity.Song;
import lombok.Data;
import java.util.Map;

@Data
public class EdgeDetailDTO {
    // 边本身的属性 (跳过次数、选择次数等)
    private Map<String, Object> edge;
    
    // 源点
    private Song source;
    
    // 目标点
    private Song target;
}