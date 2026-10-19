package com.airtribe.smart_parking_lot_system.integration;

import com.airtribe.smart_parking_lot_system.model.enums.SpotType;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP-level behaviour: status codes and error mapping (gap 8). */
class ParkingApiTest extends AbstractPostgresTest {

    private static final String BASE = "/api/v1/parking";
    private static final String ENTRY_CAR = "{\"plateNo\":\"TS09AB1234\",\"vehicleType\":\"CAR\"}";

    @Autowired MockMvc mockMvc;

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void blankPlate_returns400_withFieldErrors() throws Exception {
        postJson(BASE + "/entry", "{\"plateNo\":\"\",\"vehicleType\":\"CAR\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.plateNo").exists());
    }

    @Test
    void unknownVehicleType_returns400() throws Exception {
        postJson(BASE + "/entry", "{\"plateNo\":\"TS09AB1234\",\"vehicleType\":\"TRUCK\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void punctuationOnlyPlate_returns400() throws Exception {
        postJson(BASE + "/entry", "{\"plateNo\":\"---\",\"vehicleType\":\"CAR\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void entry_returns201_thenRetryReturns200WithSameTicket() throws Exception {
        seedSpots(1, SpotType.CAR, 2);
        postJson(BASE + "/entry", ENTRY_CAR)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketCode").exists())
                .andExpect(jsonPath("$.existingTicket").value(false));
        postJson(BASE + "/entry", ENTRY_CAR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existingTicket").value(true));
    }

    @Test
    void lotFull_returns409() throws Exception {
        postJson(BASE + "/entry", ENTRY_CAR).andExpect(status().isConflict());
    }

    @Test
    void exitUnknownTicket_returns404() throws Exception {
        mockMvc.perform(post(BASE + "/exit/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void exitWithMalformedTicketCode_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/exit/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void payBeforeExit_returns409() throws Exception {
        seedSpots(1, SpotType.CAR, 1);
        MvcResult entry = postJson(BASE + "/entry", ENTRY_CAR).andReturn();
        String code = JsonPath.read(entry.getResponse().getContentAsString(), "$.ticketCode");

        postJson(BASE + "/tickets/" + code + "/pay", "{\"paymentReference\":\"R1\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void availability_listsFloors() throws Exception {
        seedSpots(1, SpotType.CAR, 2);
        mockMvc.perform(get(BASE + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.floors[0].floor").value(1))
                .andExpect(jsonPath("$.floors[0].free.CAR").value(2))
                .andExpect(jsonPath("$.totalFree.CAR").value(2));
    }

    @Test
    void wrongHttpMethod_returns405_notAnInternalError() throws Exception {
        mockMvc.perform(get(BASE + "/entry")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void adminAddSpots_returns201() throws Exception {
        postJson("/api/v1/admin/spots", "{\"floor\":2,\"spotType\":\"BUS\",\"count\":2}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].spotNumber").value(1))
                .andExpect(jsonPath("$[1].spotNumber").value(2));
    }

    @Test
    void adminStatusWithoutBody_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/spots/1/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
