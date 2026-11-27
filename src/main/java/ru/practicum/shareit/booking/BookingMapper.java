package ru.practicum.shareit.booking;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ru.practicum.shareit.booking.dto.BookingDto;
import ru.practicum.shareit.booking.dto.BookingRequestDto;
import ru.practicum.shareit.booking.dto.BookingShortDto;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.user.User;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BookingMapper {
    public static Booking toModel(BookingRequestDto dto, Item item, User booker, BookingStatus status) {
        return new Booking(null, dto.start(), dto.end(), item, booker, status);
    }

    public static BookingDto toDto(Booking booking) {
        return new BookingDto(booking.getId(), booking.getStart(), booking.getEnd(),
                new BookingDto.ItemSummary(booking.getItem().getId(), booking.getItem().getName()),
                new BookingDto.BookerSummary(booking.getBooker().getId()), booking.getStatus());
    }

    public static BookingShortDto toShortDto(Booking booking) {
        return new BookingShortDto(booking.getId(), booking.getBooker().getId(), booking.getStart(), booking.getEnd());
    }
}
