package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.PlayerResource;
import com.galaxyempire.game.repository.PlayerResourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DarkMatterServiceTest {

    @Mock
    private PlayerResourceRepository repository;

    @InjectMocks
    private DarkMatterService service;

    @Test
    void getDarkMatterReturnsZeroWhenNoRecord() {
        when(repository.findByPlayerId(1L)).thenReturn(Optional.empty());
        assertThat(service.getDarkMatter(1L)).isZero();
    }

    @Test
    void getDarkMatterReturnsStoredBalance() {
        PlayerResource pr = new PlayerResource(1L);
        pr.setDarkMatter(42);
        when(repository.findByPlayerId(1L)).thenReturn(Optional.of(pr));
        assertThat(service.getDarkMatter(1L)).isEqualTo(42);
    }

    @Test
    void addDarkMatterCreatesRecordWhenMissing() {
        when(repository.findByPlayerId(1L)).thenReturn(Optional.empty());
        when(repository.save(any(PlayerResource.class))).thenAnswer(inv -> inv.getArgument(0));

        service.addDarkMatter(1L, 10);

        verify(repository, atLeastOnce()).save(argThat(pr -> pr.getDarkMatter() == 10));
    }

    @Test
    void addDarkMatterIncrementsExistingBalance() {
        PlayerResource pr = new PlayerResource(1L);
        pr.setDarkMatter(5);
        when(repository.findByPlayerId(1L)).thenReturn(Optional.of(pr));
        when(repository.save(any(PlayerResource.class))).thenAnswer(inv -> inv.getArgument(0));

        service.addDarkMatter(1L, 7);

        assertThat(pr.getDarkMatter()).isEqualTo(12);
    }

    @Test
    void spendDarkMatterFailsWhenNoRecord() {
        when(repository.findByPlayerId(1L)).thenReturn(Optional.empty());
        assertThat(service.spendDarkMatter(1L, 5)).isFalse();
    }

    @Test
    void spendDarkMatterFailsWhenInsufficient() {
        PlayerResource pr = new PlayerResource(1L);
        pr.setDarkMatter(3);
        when(repository.findByPlayerId(1L)).thenReturn(Optional.of(pr));

        assertThat(service.spendDarkMatter(1L, 5)).isFalse();
        assertThat(pr.getDarkMatter()).isEqualTo(3);
        verify(repository, never()).save(any());
    }

    @Test
    void spendDarkMatterDeductsWhenSufficient() {
        PlayerResource pr = new PlayerResource(1L);
        pr.setDarkMatter(10);
        when(repository.findByPlayerId(1L)).thenReturn(Optional.of(pr));
        when(repository.save(any(PlayerResource.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.spendDarkMatter(1L, 4)).isTrue();
        assertThat(pr.getDarkMatter()).isEqualTo(6);
    }

    @Test
    void speedUpCostIsZeroForCompletedAction() {
        assertThat(DarkMatterService.calculateSpeedUpCost(0)).isZero();
        assertThat(DarkMatterService.calculateSpeedUpCost(-100)).isZero();
    }

    @Test
    void speedUpCostRoundsUpPerHalfHour() {
        assertThat(DarkMatterService.calculateSpeedUpCost(1)).isEqualTo(1);
        assertThat(DarkMatterService.calculateSpeedUpCost(1800)).isEqualTo(1);
        assertThat(DarkMatterService.calculateSpeedUpCost(1801)).isEqualTo(2);
        assertThat(DarkMatterService.calculateSpeedUpCost(3600)).isEqualTo(2);
    }
}
