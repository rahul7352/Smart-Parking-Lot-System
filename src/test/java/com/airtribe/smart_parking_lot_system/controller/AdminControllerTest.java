package com.airtribe.smart_parking_lot_system.controller;

import com.airtribe.smart_parking_lot_system.exception.InvalidSpotStateException;
import com.airtribe.smart_parking_lot_system.exception.SpotNotFoundException;
import com.airtribe.smart_parking_lot_system.model.dto.AddSpotRequest;
import com.airtribe.smart_parking_lot_system.model.dto.SpotResponse;
import com.airtribe.smart_parking_lot_system.model.enums.SpotStatus;
import com.airtribe.smart_parking_lot_system.model.enums.SpotType;
import com.airtribe.smart_parking_lot_system.service.SpotAdminService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean
    SpotAdminService adminService;

    private org.springframework.test.web.servlet.ResultActions send(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, String json)
            throws Exception {
        return mockMvc.perform(builder.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void addSpots_returns201_withCreatedSpots() throws Exception {
        when(adminService.addSpots(any(AddSpotRequest.class))).thenReturn(List.of(
                new SpotResponse(1L, 2, 1, SpotType.BUS, SpotStatus.FREE),
                new SpotResponse(2L, 2, 2, SpotType.BUS, SpotStatus.FREE)));

        send(post("/api/v1/admin/spots"), "{\"floor\":2,\"spotType\":\"BUS\",\"count\":2}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].spotNumber").value(1))
                .andExpect(jsonPath("$[1].spotType").value("BUS"));

        ArgumentCaptor<AddSpotRequest> captor = ArgumentCaptor.forClass(AddSpotRequest.class);
        verify(adminService).addSpots(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new AddSpotRequest(2, SpotType.BUS, 2));
    }

    @Test
    void addSpots_invalidInput_returns400() throws Exception {
        send(post("/api/v1/admin/spots"), "{\"floor\":0,\"spotType\":\"BUS\",\"count\":2}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.floor").exists());
        send(post("/api/v1/admin/spots"), "{\"floor\":1,\"spotType\":\"BUS\",\"count\":501}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.count").exists());
        send(post("/api/v1/admin/spots"), "{\"floor\":1,\"count\":5}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.spotType").exists());
        send(post("/api/v1/admin/spots"), "{\"floor\":1,\"spotType\":\"HELIPAD\",\"count\":5}")
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminService);
    }

    @Test
    void setStatus_returnsUpdatedSpot() throws Exception {
        when(adminService.setStatus(7L, SpotStatus.OUT_OF_SERVICE))
                .thenReturn(new SpotResponse(7L, 1, 3, SpotType.CAR, SpotStatus.OUT_OF_SERVICE));

        send(patch("/api/v1/admin/spots/7/status"), "{\"status\":\"OUT_OF_SERVICE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));
    }

    @Test
    void setStatus_missingStatus_returns400() throws Exception {
        send(patch("/api/v1/admin/spots/7/status"), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
        verifyNoInteractions(adminService);
    }

    @Test
    void setStatus_unknownSpot_returns404() throws Exception {
        when(adminService.setStatus(eq(99L), any())).thenThrow(new SpotNotFoundException(99L));

        send(patch("/api/v1/admin/spots/99/status"), "{\"status\":\"FREE\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void setStatus_occupiedSpot_returns409() throws Exception {
        when(adminService.setStatus(eq(7L), any()))
                .thenThrow(new InvalidSpotStateException("Spot is occupied; wait until the vehicle leaves"));

        send(patch("/api/v1/admin/spots/7/status"), "{\"status\":\"OUT_OF_SERVICE\"}")
                .andExpect(status().isConflict());
    }
}
