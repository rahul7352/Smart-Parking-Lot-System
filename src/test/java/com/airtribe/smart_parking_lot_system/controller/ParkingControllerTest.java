package com.airtribe.smart_parking_lot_system.controller;

import com.airtribe.smart_parking_lot_system.exception.*;
import com.airtribe.smart_parking_lot_system.model.dto.*;
import com.airtribe.smart_parking_lot_system.model.enums.SpotType;
import com.airtribe.smart_parking_lot_system.model.enums.TicketStatus;
import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;
import com.airtribe.smart_parking_lot_system.service.AvailabilityService;
import com.airtribe.smart_parking_lot_system.service.ParkingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Web layer only: routing, JSON mapping, validation and error mapping. Services are mocked. */
@WebMvcTest(ParkingController.class)
class ParkingControllerTest {

    private static final String BASE = "/api/v1/parking";
    private static final String ENTRY_CAR = "{\"plateNumber\":\"TS09AB1234\",\"vehicleType\":\"CAR\"}";
    private static final UUID CODE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant ENTRY_TIME = Instant.parse("2026-10-15T10:00:00Z");
    private static final Instant EXIT_TIME = Instant.parse("2026-10-15T11:01:00Z");

    @Autowired MockMvc mockMvc;
    @MockitoBean
    ParkingService parkingService;
    @MockitoBean
    AvailabilityService availabilityService;

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static ExitResponse receipt(TicketStatus status) {
        return new ExitResponse(CODE, "TS09AB1234", ENTRY_TIME, EXIT_TIME, 61, new BigDecimal("60.00"), status);
    }

    // ---------- POST /entry ----------

    @Test
    void entry_newTicket_returns201_andPassesTheRequestToTheService() throws Exception {
        when(parkingService.enter(any(EntryRequest.class)))
                .thenReturn(new EntryResponse(CODE, 1, 4, ENTRY_TIME, false));

        postJson(BASE + "/entry", ENTRY_CAR)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketCode").value(CODE.toString()))
                .andExpect(jsonPath("$.floorNumber").value(1))
                .andExpect(jsonPath("$.spotNumber").value(4))
                .andExpect(jsonPath("$.entryTime").value("2026-10-15T10:00:00Z"))
                .andExpect(jsonPath("$.existingTicket").value(false));

        ArgumentCaptor<EntryRequest> captor = ArgumentCaptor.forClass(EntryRequest.class);
        verify(parkingService).enter(captor.capture());
        assertThat(captor.getValue().plateNumber()).isEqualTo("TS09AB1234");
        assertThat(captor.getValue().vehicleType()).isEqualTo(VehicleType.CAR);
    }

    @Test
    void entry_alreadyParked_returns200_withExistingTicketFlag() throws Exception {
        when(parkingService.enter(any(EntryRequest.class)))
                .thenReturn(new EntryResponse(CODE, 1, 4, ENTRY_TIME, true));

        postJson(BASE + "/entry", ENTRY_CAR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existingTicket").value(true));
    }

    @Test
    void entry_blankPlate_returns400_withFieldErrors_andNeverCallsTheService() throws Exception {
        postJson(BASE + "/entry", "{\"plateNumber\":\"\",\"vehicleType\":\"CAR\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.plateNumber").exists());

        verifyNoInteractions(parkingService);
    }

    @Test
    void entry_plateWithIllegalCharacters_returns400() throws Exception {
        postJson(BASE + "/entry", "{\"plateNumber\":\"TS09@AB\",\"vehicleType\":\"CAR\"}")
                .andExpect(status().isBadRequest());
        verifyNoInteractions(parkingService);
    }

    @Test
    void entry_missingVehicleType_returns400() throws Exception {
        postJson(BASE + "/entry", "{\"plateNumber\":\"TS09AB1234\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.vehicleType").exists());
    }

    @Test
    void entry_unknownVehicleType_returns400() throws Exception {
        postJson(BASE + "/entry", "{\"plateNumber\":\"TS09AB1234\",\"vehicleType\":\"TRUCK\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void entry_malformedJson_returns400() throws Exception {
        postJson(BASE + "/entry", "{not json").andExpect(status().isBadRequest());
    }

    @Test
    void entry_wrongHttpMethod_returns405() throws Exception {
        mockMvc.perform(get(BASE + "/entry")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void entry_lotFull_returns409() throws Exception {
        when(parkingService.enter(any(EntryRequest.class)))
                .thenThrow(new NoSpotAvailableException("No free spot for CAR"));

        postJson(BASE + "/entry", ENTRY_CAR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No free spot for CAR"));
    }

    @Test
    void entry_paymentPending_returns409() throws Exception {
        when(parkingService.enter(any(EntryRequest.class)))
                .thenThrow(new PaymentPendingException("TS09AB1234"));

        postJson(BASE + "/entry", ENTRY_CAR).andExpect(status().isConflict());
    }

    @Test
    void entry_parkingBusy_returns503_withRetryAfterHeader() throws Exception {
        when(parkingService.enter(any(EntryRequest.class)))
                .thenThrow(new ParkingBusyException("Parking is busy, please retry shortly"));

        postJson(BASE + "/entry", ENTRY_CAR)
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"));
    }

    @Test
    void entry_serviceRejectsPlate_returns400() throws Exception {
        when(parkingService.enter(any(EntryRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid plate number"));

        postJson(BASE + "/entry", "{\"plateNumber\":\"---\",\"vehicleType\":\"CAR\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid plate number"));
    }

    @Test
    void entry_databaseConflict_returns409_withoutLeakingSql() throws Exception {
        when(parkingService.enter(any(EntryRequest.class))).thenThrow(
                new DataIntegrityViolationException("duplicate key value violates constraint uq_secret"));

        postJson(BASE + "/entry", ENTRY_CAR)
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("uq_secret"))));
    }

    @Test
    void entry_lockFailure_returns409_retryMessage() throws Exception {
        when(parkingService.enter(any(EntryRequest.class)))
                .thenThrow(new CannotAcquireLockException("lock timeout on table spot"));

        postJson(BASE + "/entry", ENTRY_CAR)
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("lock timeout"))));
    }

    @Test
    void entry_unexpectedError_returns500_withGenericMessage() throws Exception {
        when(parkingService.enter(any(EntryRequest.class)))
                .thenThrow(new RuntimeException("insert into secret_table failed"));

        postJson(BASE + "/entry", ENTRY_CAR)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Unexpected server error"))
                .andExpect(content().string(not(containsString("secret_table"))));
    }

    // ---------- POST /exit/{ticketCode} ----------

    @Test
    void exit_returnsReceipt() throws Exception {
        when(parkingService.exit(CODE)).thenReturn(receipt(TicketStatus.PAYMENT_PENDING));

        mockMvc.perform(post(BASE + "/exit/" + CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketCode").value(CODE.toString()))
                .andExpect(jsonPath("$.plateNumber").value("TS09AB1234"))
                .andExpect(jsonPath("$.durationMinutes").value(61))
                .andExpect(jsonPath("$.fee").value(60.0))
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"));
    }

    @Test
    void exit_unknownTicket_returns404() throws Exception {
        when(parkingService.exit(CODE)).thenThrow(new TicketNotFoundException(CODE.toString()));

        mockMvc.perform(post(BASE + "/exit/" + CODE)).andExpect(status().isNotFound());
    }

    @Test
    void exit_malformedTicketCode_returns400_andNeverCallsTheService() throws Exception {
        mockMvc.perform(post(BASE + "/exit/not-a-uuid")).andExpect(status().isBadRequest());
        verifyNoInteractions(parkingService);
    }

    // ---------- POST /exit/by-plate ----------

    @Test
    void exitByPlate_returnsReceipt() throws Exception {
        when(parkingService.exitByPlateNumber("ts09ab1234")).thenReturn(receipt(TicketStatus.CLOSED));

        postJson(BASE + "/exit/by-plate", "{\"plateNumber\":\"ts09ab1234\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void exitByPlate_blankPlate_returns400() throws Exception {
        postJson(BASE + "/exit/by-plate", "{\"plateNumber\":\"\"}").andExpect(status().isBadRequest());
        verifyNoInteractions(parkingService);
    }

    @Test
    void exitByPlate_noOpenTicket_returns404() throws Exception {
        when(parkingService.exitByPlateNumber("TS09AB1234")).thenThrow(new TicketNotFoundException("TS09AB1234"));

        postJson(BASE + "/exit/by-plate", "{\"plateNumber\":\"TS09AB1234\"}").andExpect(status().isNotFound());
    }

    // ---------- POST /tickets/{ticketCode}/pay ----------

    @Test
    void pay_returnsClosedReceipt() throws Exception {
        when(parkingService.pay(CODE, "UPI-1")).thenReturn(receipt(TicketStatus.CLOSED));

        postJson(BASE + "/tickets/" + CODE + "/pay", "{\"paymentReference\":\"UPI-1\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void pay_missingReference_returns400() throws Exception {
        postJson(BASE + "/tickets/" + CODE + "/pay", "{\"paymentReference\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.paymentReference").exists());
        verifyNoInteractions(parkingService);
    }

    @Test
    void pay_beforeCheckout_returns409() throws Exception {
        when(parkingService.pay(CODE, "UPI-1"))
                .thenThrow(new InvalidTicketStateException("Ticket must be checked out (exit) before payment"));

        postJson(BASE + "/tickets/" + CODE + "/pay", "{\"paymentReference\":\"UPI-1\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void pay_unknownTicket_returns404() throws Exception {
        when(parkingService.pay(CODE, "UPI-1")).thenThrow(new TicketNotFoundException(CODE.toString()));

        postJson(BASE + "/tickets/" + CODE + "/pay", "{\"paymentReference\":\"UPI-1\"}")
                .andExpect(status().isNotFound());
    }

    // ---------- GET /availability ----------

    @Test
    void availability_returnsTotalsAndFloors() throws Exception {
        when(availabilityService.availability()).thenReturn(new AvailabilityResponse(
                Map.of(SpotType.CAR, 2L),
                List.of(new FloorAvailability(1, Map.of(SpotType.CAR, 2L)))));

        mockMvc.perform(get(BASE + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFree.CAR").value(2))
                .andExpect(jsonPath("$.floors[0].floor").value(1))
                .andExpect(jsonPath("$.floors[0].free.CAR").value(2));
    }
}
