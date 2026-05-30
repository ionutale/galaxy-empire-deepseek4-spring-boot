package com.galaxyempire.game.web;

import com.galaxyempire.game.service.BuildingService;
import com.galaxyempire.game.service.DarkMatterService;
import com.galaxyempire.game.service.EconomyService;
import com.galaxyempire.game.service.PlanetService;
import com.galaxyempire.game.service.ShipyardService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the debug-only dark-matter grant endpoint is gated by
 * {@code game.debug.endpoints-enabled}.
 */
class PlanetControllerDebugEndpointTest {

    private final DarkMatterService darkMatterService = mock(DarkMatterService.class);

    private MockMvc mockMvcWithDebug(boolean enabled) {
        PlanetController controller = new PlanetController(
                mock(PlanetService.class), mock(EconomyService.class), darkMatterService,
                mock(BuildingService.class), mock(ShipyardService.class), enabled);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void addDarkMatterIsForbiddenWhenDebugDisabled() throws Exception {
        mockMvcWithDebug(false)
                .perform(post("/api/game/players/3/dark-matter/add")
                        .contentType("application/json").content("{\"amount\":100}"))
                .andExpect(status().isForbidden());

        verify(darkMatterService, never()).addDarkMatter(anyLong(), anyInt());
    }

    @Test
    void addDarkMatterGrantsWhenDebugEnabled() throws Exception {
        when(darkMatterService.getDarkMatter(3L)).thenReturn(100);

        mockMvcWithDebug(true)
                .perform(post("/api/game/players/3/dark-matter/add")
                        .contentType("application/json").content("{\"amount\":100}"))
                .andExpect(status().isOk());

        verify(darkMatterService).addDarkMatter(3L, 100);
    }
}
