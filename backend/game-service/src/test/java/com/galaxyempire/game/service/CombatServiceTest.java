package com.galaxyempire.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galaxyempire.game.domain.CombatReport;
import com.galaxyempire.game.domain.Fleet;
import com.galaxyempire.game.domain.FleetShip;
import com.galaxyempire.game.domain.Planet;
import com.galaxyempire.game.domain.PlanetDefense;
import com.galaxyempire.game.domain.PlanetShip;
import com.galaxyempire.game.domain.QuestEvent;
import com.galaxyempire.game.domain.ShipType;
import com.galaxyempire.game.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CombatServiceTest {

    @Mock private PlanetShipRepository planetShipRepository;
    @Mock private FleetShipRepository fleetShipRepository;
    @Mock private DebrisFieldRepository debrisFieldRepository;
    @Mock private CombatReportRepository combatReportRepository;
    @Mock private PlanetRepository planetRepository;
    @Mock private PlanetDefenseRepository planetDefenseRepository;
    @Mock private QuestService questService;

    private CombatService service;

    @BeforeEach
    void setUp() {
        service = new CombatService(planetShipRepository, fleetShipRepository, debrisFieldRepository,
                combatReportRepository, planetRepository, new GameBalancer(1.0), new ObjectMapper(),
                planetDefenseRepository, questService);
    }

    private Fleet attackingFleet() {
        Fleet fleet = mock(Fleet.class);
        when(fleet.getTargetPlanetId()).thenReturn(100L);
        when(fleet.getPlayerId()).thenReturn(7L);
        lenient().when(fleet.getId()).thenReturn(1L);
        lenient().when(fleet.getOriginPlanetId()).thenReturn(200L);
        return fleet;
    }

    @Test
    void attackerWinsAgainstUndefendedPlanetAndLootsResources() {
        Fleet fleet = attackingFleet();
        List<FleetShip> attackers = List.of(new FleetShip(1L, ShipType.LIGHT_FIGHTER, 100));

        Planet target = new Planet();
        target.setId(100L);
        target.setPlayerId(9L);
        target.setMetal(100000);
        target.setCrystal(100000);
        target.setGas(100000);

        when(planetShipRepository.findByPlanetId(100L)).thenReturn(List.<PlanetShip>of());
        when(planetDefenseRepository.findByPlanetId(100L)).thenReturn(List.<PlanetDefense>of());
        when(planetRepository.findById(100L)).thenReturn(Optional.of(target));
        when(fleetShipRepository.findByFleetId(1L)).thenReturn(List.<FleetShip>of());
        when(combatReportRepository.save(any(CombatReport.class))).thenAnswer(inv -> inv.getArgument(0));

        CombatReport report = service.resolveCombat(fleet, attackers);

        assertThat(report.getResult()).isEqualTo("ATTACKER_WIN");
        assertThat(report.getAttackerId()).isEqualTo(7L);
        assertThat(report.getDefenderId()).isEqualTo(9L);
        // 100 light fighters * 50 cargo = 5000 capacity; one third looted as metal.
        assertThat(target.getMetal()).isLessThan(100000);
    }

    @Test
    void attackerWinEmitsBattleWonQuestEvent() {
        Fleet fleet = attackingFleet();
        List<FleetShip> attackers = List.of(new FleetShip(1L, ShipType.BATTLESHIP, 50));

        Planet target = new Planet();
        target.setId(100L);
        target.setPlayerId(9L);

        when(planetShipRepository.findByPlanetId(100L)).thenReturn(List.<PlanetShip>of());
        when(planetDefenseRepository.findByPlanetId(100L)).thenReturn(List.<PlanetDefense>of());
        when(planetRepository.findById(100L)).thenReturn(Optional.of(target));
        when(fleetShipRepository.findByFleetId(1L)).thenReturn(List.<FleetShip>of());
        when(combatReportRepository.save(any(CombatReport.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resolveCombat(fleet, attackers);

        verify(questService).processQuestEvent(argThat((QuestEvent e) ->
                e.playerId().equals(7L) && e.requirementType().equals("BATTLE_WON") && e.value() == 1));
    }
}
