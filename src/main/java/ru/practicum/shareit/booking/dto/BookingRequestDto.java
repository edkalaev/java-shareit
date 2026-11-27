package ru.practicum.shareit.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record BookingRequestDto(@NotNull @Positive Long itemId, @NotNull LocalDateTime start,
                                @NotNull LocalDateTime end) {
}
