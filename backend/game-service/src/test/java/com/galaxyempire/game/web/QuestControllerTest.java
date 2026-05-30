package com.galaxyempire.game.web;

import com.galaxyempire.game.service.QuestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QuestControllerTest {

    private QuestService questService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        questService = mock(QuestService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new QuestController(questService)).build();
    }

    @Test
    void getQuestsReturnsQuestListForPlayer() throws Exception {
        when(questService.getAvailableQuests(7L)).thenReturn(List.of(
                Map.of("title", "First Steps", "completed", false)));

        mockMvc.perform(get("/api/game/quests").header("X-Player-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("First Steps"));

        verify(questService).getAvailableQuests(7L);
    }

    @Test
    void claimRewardReturnsOkOnSuccess() throws Exception {
        when(questService.claimReward(7L, 3L)).thenReturn(
                Map.of("success", true, "rewardType", "DARK_MATTER", "rewardAmount", 25));

        mockMvc.perform(post("/api/game/quests/3/claim").header("X-Player-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.rewardAmount").value(25));
    }

    @Test
    void claimRewardReturnsBadRequestOnInvalidClaim() throws Exception {
        when(questService.claimReward(eq(7L), eq(3L)))
                .thenThrow(new IllegalArgumentException("Already claimed"));

        mockMvc.perform(post("/api/game/quests/3/claim").header("X-Player-Id", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Already claimed"));
    }
}
