package ru.practicum.shareit.booking.dto;

import ru.practicum.shareit.booking.BookingStatus;

import java.time.LocalDateTime;

public record BookingDto(Long id, LocalDateTime start, LocalDateTime end,
                         ItemSummary item, BookerSummary booker, BookingStatus status) {
    public record ItemSummary(Long id, String name) {
    }

    public record BookerSummary(Long id) {
    }
}
