package com.airtribe.smart_parking_lot_system.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PayRequest(@NotBlank @Size(max = 100) String paymentReference) {
}
