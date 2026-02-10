package com.songmap.songmap.service;

public class RankWeights {
    // 互动权重
    public static final double W_USER_SELECT = 5.0;  // 主动点播
    public static final double W_JUMP = 1.0;         // 自然跳转
    public static final double W_RANDOM = 0.8;       // 随机选中 (作为减分项或分母)

    // 方向系数
    public static final double DIR_FORWARD = 1.0;
    public static final double DIR_BACKWARD = 0.5;   // 反向边降权
    public static final double DIR_REPEAT = 0.1;     // 回头路极刑

    // 冷却系数 (牛顿冷却定律)
    // 假设 0.01，意味着大约 60-100 分钟后新鲜度恢复得差不多
    public static final double COOLING_LAMBDA = 0.01; 
}