package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.PlayerResource;
import com.galaxyempire.game.repository.PlayerResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DarkMatterService {

    private final PlayerResourceRepository playerResourceRepository;

    public DarkMatterService(PlayerResourceRepository playerResourceRepository) {
        this.playerResourceRepository = playerResourceRepository;
    }

    @Transactional(readOnly = true)
    public int getDarkMatter(Long playerId) {
        return playerResourceRepository.findByPlayerId(playerId)
            .map(PlayerResource::getDarkMatter)
            .orElse(0);
    }

    @Transactional
    public void addDarkMatter(Long playerId, int amount) {
        PlayerResource pr = playerResourceRepository.findByPlayerId(playerId)
            .orElseGet(() -> playerResourceRepository.save(new PlayerResource(playerId)));
        pr.setDarkMatter(pr.getDarkMatter() + amount);
        playerResourceRepository.save(pr);
    }

    @Transactional
    public boolean spendDarkMatter(Long playerId, int amount) {
        PlayerResource pr = playerResourceRepository.findByPlayerId(playerId).orElse(null);
        if (pr == null || pr.getDarkMatter() < amount) {
            return false;
        }
        pr.setDarkMatter(pr.getDarkMatter() - amount);
        playerResourceRepository.save(pr);
        return true;
    }

    public static int calculateSpeedUpCost(long remainingSeconds) {
        if (remainingSeconds <= 0) return 0;
        return Math.max(1, (int) Math.ceil(remainingSeconds / 1800.0));
    }
}
