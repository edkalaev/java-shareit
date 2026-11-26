package ru.practicum.shareit.booking;

import ru.practicum.shareit.booking.dto.BookingDto;
import ru.practicum.shareit.booking.dto.BookingRequestDto;

import java.util.List;

public interface BookingService {
    BookingDto create(long bookerId, BookingRequestDto dto);

    BookingDto approve(long ownerId, long bookingId, boolean approved);

    BookingDto getById(long userId, long bookingId);

    List<BookingDto> getAllByBooker(long bookerId, BookingState state);

    List<BookingDto> getAllByOwner(long ownerId, BookingState state);
}
