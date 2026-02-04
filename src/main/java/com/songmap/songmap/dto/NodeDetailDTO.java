package com.songmap.songmap.dto;

import com.songmap.songmap.entity.Song;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class NodeDetailDTO {
    // 点本身的属性
    private Song self;
    
    // 出边集：包含 { "edge": 边属性, "target": 目标点属性 }
    private List<Map<String, Object>> outgoing;
    
    // 入边集：包含 { "edge": 边属性, "source": 源点属性 }
    private List<Map<String, Object>> incoming;
}