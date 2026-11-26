package ru.practicum.shareit.booking;

import org.junit.jupiter.api.Test;
import ru.practicum.shareit.booking.dto.BookingRequestDto;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.user.User;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BookingMapperTests {
    @Test
    void mapsResolvedEntitiesToCompactResponses() {
        LocalDateTime start = LocalDateTime.of(2028, 3, 10, 12, 0);
        User owner = new User(1L, "Owner", "owner@example.org");
        User booker = new User(2L, "Booker", "booker@example.org");
        Item item = new Item(3L, "Drill", "Power tool", true, owner);
        BookingRequestDto request = new BookingRequestDto(item.getId(), start, start.plusHours(2));

        Booking booking = BookingMapper.toModel(request, item, booker, BookingStatus.WAITING);
        assertThat(booking.getId()).isNull();
        booking.setId(4L);

        var dto = BookingMapper.toDto(booking);
        var shortDto = BookingMapper.toShortDto(booking);
        assertThat(dto.item().name()).isEqualTo("Drill");
        assertThat(dto.booker().id()).isEqualTo(booker.getId());
        assertThat(dto.status()).isEqualTo(BookingStatus.WAITING);
        assertThat(shortDto.id()).isEqualTo(dto.id());
        assertThat(shortDto.bookerId()).isEqualTo(dto.booker().id());
        assertThat(shortDto.start()).isEqualTo(request.start());
        assertThat(shortDto.end()).isEqualTo(request.end());
    }
}
