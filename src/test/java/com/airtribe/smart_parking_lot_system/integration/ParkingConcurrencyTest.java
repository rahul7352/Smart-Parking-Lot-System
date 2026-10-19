package com.airtribe.smart_parking_lot_system.integration;

import com.airtribe.smart_parking_lot_system.exception.NoSpotAvailableException;
import com.airtribe.smart_parking_lot_system.exception.ParkingBusyException;
import com.airtribe.smart_parking_lot_system.model.dto.EntryRequest;
import com.airtribe.smart_parking_lot_system.model.dto.EntryResponse;
import com.airtribe.smart_parking_lot_system.model.dto.ExitResponse;
import com.airtribe.smart_parking_lot_system.model.enums.SpotStatus;
import com.airtribe.smart_parking_lot_system.model.enums.SpotType;
import com.airtribe.smart_parking_lot_system.model.enums.TicketStatus;
import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;
import com.airtribe.smart_parking_lot_system.service.AvailabilityService;
import com.airtribe.smart_parking_lot_system.service.ParkingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ParkingConcurrencyTest extends AbstractPostgresTest {

    @Autowired
    ParkingService parkingService;
    @Autowired
    AvailabilityService availabilityService;

    /** Releases all tasks at the same instant and returns each task's result or thrown exception. */
    private <T> List<Object> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return task.call();
                } catch (Exception e) {
                    return e;
                }
            }));
        }
        ready.await();
        go.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> f : futures) results.add(f.get(60, TimeUnit.SECONDS));
        pool.shutdown();
        return results;
    }

    @Test
    void fiftyVehiclesRacingForTenSpots_exactlyTenSucceed_noDoubleAllocation() throws Exception {
        seedSpots(1, SpotType.CAR, 10);
        List<Callable<EntryResponse>> tasks = IntStream.range(0, 50)
                .<Callable<EntryResponse>>mapToObj(i -> () ->
                        parkingService.enter(new EntryRequest("PLATE-" + i, VehicleType.CAR)))
                .toList();

        List<Object> results = runConcurrently(tasks);

        List<EntryResponse> ok = results.stream()
                .filter(EntryResponse.class::isInstance).map(EntryResponse.class::cast).toList();
        List<Object> failed = results.stream().filter(r -> !(r instanceof EntryResponse)).toList();

        assertThat(ok).hasSize(10);
        // "full" is the expected failure; "busy" is the (rare) back-off exhaustion under heavy contention
        assertThat(failed).hasSize(40)
                .allMatch(e -> e instanceof NoSpotAvailableException || e instanceof ParkingBusyException);
        assertThat(ok.stream().map(EntryResponse::spotNumber).collect(Collectors.toSet())).hasSize(10);
        assertThat(availabilityService.freeSpots().get(SpotType.CAR)).isZero();
        assertThat(spotRepository.findAll()).allMatch(s -> s.getStatus() == SpotStatus.OCCUPIED);
    }

    @Test
    void samePlateEnteringConcurrently_oneTicket_everyoneGetsIt() throws Exception {
        seedSpots(1, SpotType.CAR, 10);
        List<Callable<EntryResponse>> tasks = IntStream.range(0, 8)
                .<Callable<EntryResponse>>mapToObj(i -> () ->
                        parkingService.enter(new EntryRequest("DUPLICATE-1", VehicleType.CAR)))
                .toList();

        List<Object> results = runConcurrently(tasks);

        assertThat(results).allMatch(r -> r instanceof EntryResponse);
        List<EntryResponse> responses = results.stream().map(EntryResponse.class::cast).toList();
        assertThat(responses.stream().map(EntryResponse::ticketCode).collect(Collectors.toSet())).hasSize(1);
        assertThat(responses.stream().filter(r -> !r.existingTicket())).hasSize(1);
        assertThat(ticketRepository.count()).isEqualTo(1);
        assertThat(availabilityService.freeSpots().get(SpotType.CAR)).isEqualTo(9L);
    }

    @Test
    void concurrentExitsOnSameTicket_areIdempotent_andSpotIsFreedOnce() throws Exception {
        seedSpots(1, SpotType.CAR, 10);
        EntryResponse entry = parkingService.enter(new EntryRequest("EXIT-1", VehicleType.CAR));
        List<Callable<ExitResponse>> tasks = IntStream.range(0, 10)
                .<Callable<ExitResponse>>mapToObj(i -> () -> parkingService.exit(entry.ticketCode()))
                .toList();

        List<Object> results = runConcurrently(tasks);

        assertThat(results).allMatch(r -> r instanceof ExitResponse);
        assertThat(results.stream().map(r -> ((ExitResponse) r).exitTime()).collect(Collectors.toSet()))
                .hasSize(1);
        assertThat(availabilityService.freeSpots().get(SpotType.CAR)).isEqualTo(10L);
    }

    @Test
    void concurrentPaymentsOnSameTicket_closeOnce() throws Exception {
        seedSpots(1, SpotType.CAR, 1);
        EntryResponse entry = parkingService.enter(new EntryRequest("PAY-0001", VehicleType.CAR));
        clock.advance(Duration.ofHours(2));
        assertThat(parkingService.exit(entry.ticketCode()).status()).isEqualTo(TicketStatus.PAYMENT_PENDING);

        List<Callable<ExitResponse>> tasks = IntStream.range(0, 10)
                .<Callable<ExitResponse>>mapToObj(i -> () -> parkingService.pay(entry.ticketCode(), "REF-1"))
                .toList();

        List<Object> results = runConcurrently(tasks);

        assertThat(results).allMatch(r -> r instanceof ExitResponse
                && ((ExitResponse) r).status() == TicketStatus.CLOSED);
        assertThat(ticketRepository.findAll()).hasSize(1)
                .allMatch(t -> "REF-1".equals(t.getPaymentReference()));
        assertThat(availabilityService.freeSpots().get(SpotType.CAR)).isEqualTo(1L);
    }
}
