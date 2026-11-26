package ru.practicum.shareit.booking;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.practicum.shareit.booking.dto.BookingDto;
import ru.practicum.shareit.booking.dto.BookingRequestDto;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.exception.ValidationException;
import ru.practicum.shareit.item.ItemRepository;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.user.User;
import ru.practicum.shareit.user.UserRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingServiceImpl implements BookingService {
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final Clock clock;

    @Override
    @Transactional
    public BookingDto create(long bookerId, BookingRequestDto dto) {
        User booker = userRepository.findById(bookerId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Item item = itemRepository.findForUpdate(dto.itemId())
                .orElseThrow(() -> new NotFoundException("Item not found"));
        if (item.getOwner().getId() == bookerId) {
            throw new NotFoundException("Owner cannot book their own item");
        }
        if (!item.getAvailable()) {
            throw new ValidationException("Item is unavailable");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!dto.start().isAfter(now) || !dto.end().isAfter(dto.start())) {
            throw new ValidationException("Booking must start in the future and end after it starts");
        }

        Booking booking = BookingMapper.toModel(dto, item, booker, BookingStatus.WAITING);
        return BookingMapper.toDto(bookingRepository.save(booking));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingDto approve(long ownerId, long bookingId, boolean approved) {
        if (!userRepository.existsById(ownerId)) {
            throw approvalForbidden();
        }
        long itemId = bookingRepository.findItemId(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        // Read the booking status only after locking the item shared by competing approvals.
        Item item = itemRepository.findForUpdate(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        if (item.getOwner().getId() != ownerId) {
            throw approvalForbidden();
        }

        Booking booking = findBooking(bookingId);
        if (booking.getStatus() != BookingStatus.WAITING) {
            throw new ValidationException("Booking has already been processed");
        }
        if (approved && !booking.getEnd().isAfter(LocalDateTime.now(clock))) {
            throw new ValidationException("An ended booking cannot be approved");
        }
        if (approved && bookingRepository.existsByItemIdAndStatusAndStartLessThanAndEndGreaterThan(
                itemId, BookingStatus.APPROVED, booking.getEnd(), booking.getStart())) {
            throw new ValidationException("Booking overlaps an approved booking");
        }
        booking.setStatus(approved ? BookingStatus.APPROVED : BookingStatus.REJECTED);
        return BookingMapper.toDto(booking);
    }

    @Override
    public BookingDto getById(long userId, long bookingId) {
        requireUser(userId);
        Booking booking = findBooking(bookingId);
        if (booking.getBooker().getId() != userId && booking.getItem().getOwner().getId() != userId) {
            throw new NotFoundException("Booking not found");
        }
        return BookingMapper.toDto(booking);
    }

    @Override
    public List<BookingDto> getAllByBooker(long bookerId, BookingState state) {
        return getAll(bookerId, false, state);
    }

    @Override
    public List<BookingDto> getAllByOwner(long ownerId, BookingState state) {
        return getAll(ownerId, true, state);
    }

    private List<BookingDto> getAll(long userId, boolean owner, BookingState state) {
        requireUser(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        return bookingRepository.findForUser(userId, owner, state.name(), now).stream()
                .map(BookingMapper::toDto)
                .toList();
    }

    private void requireUser(long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User not found");
        }
    }

    private Booking findBooking(long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
    }

    private ResponseStatusException approvalForbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the item owner can change booking status");
    }
}
