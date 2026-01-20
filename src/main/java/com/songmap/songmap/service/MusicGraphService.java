package com.songmap.songmap.service;

import com.songmap.songmap.entity.Song;
import com.songmap.songmap.repository.SongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

@Service
public class MusicGraphService {
    private final SongRepository songRepository;

    public MusicGraphService(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    @Transactional
    public Song addSong(String name, boolean forceNewChain) {
        // 1. 【第一步】先找到“最后听的那首歌”的 影子（可能不包含关系）
        Song lastSongLite = songRepository.findLastListenedSong();

        // 2. 【关键修复】如果找到了，必须用 ID 重新加载一次“完整实体”
        // 这样 Spring 才会把数据库里已有的 nextSongs 关系加载到内存 list 里
        Song lastSong = null;
        if (lastSongLite != null) {
            // findById 默认会加载一层关系
            lastSong = songRepository.findById(lastSongLite.getId()).orElse(null);
        }

        // 提取上一首歌的信息（防御性编程，防止引用问题）
        String lastSongName = (lastSong != null) ? lastSong.getName() : "";
        LocalDate lastDate = (lastSong != null) ? lastSong.getListenedAt().toLocalDate() : null;

        // 3. 准备当前歌曲
        Song currentSong = songRepository.findByName(name)
                .orElse(new Song(name));
        currentSong.setListenedAt(LocalDateTime.now());

        // 4. 防自环逻辑
        if (lastSongName.equals(name)) {
            return songRepository.save(currentSong);
        }

        // 5. 连接逻辑
        if (lastSong != null && !forceNewChain && lastDate != null) {
            LocalDate today = currentSong.getListenedAt().toLocalDate();

            if (lastDate.equals(today)) {
                if (lastSong.getNextSongs() == null) {
                    lastSong.setNextSongs(new ArrayList<>());
                }
                
                // 建立连接：B -> A
                lastSong.getNextSongs().add(currentSong);
                
                // 【保存 B】这会自动保存 B->A 的关系以及 A 的属性修改
                songRepository.save(lastSong);
                
                // 直接返回 A 即可，不需要再次 save(currentSong)，
                // 因为上面的 save(lastSong) 已经级联保存了 A 的状态。
                return currentSong; 
            }
        }

        // 6. 保存当前歌曲
        // 这里的逻辑可以保留，用来确保 currentSong 的时间更新被保存
        // 即使上面 save(lastSong) 级联保存了 currentSong，多存一次通常无害
        return songRepository.save(currentSong);
    }

    // 你的其他方法（如 cutLastConnection）可以保留在这里
    @Transactional
    public void cutLastConnection() {
        // 暂时留空或保留你原有的逻辑
    }
}