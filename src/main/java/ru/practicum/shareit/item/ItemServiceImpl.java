package ru.practicum.shareit.item;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.booking.Booking;
import ru.practicum.shareit.booking.BookingMapper;
import ru.practicum.shareit.booking.BookingRepository;
import ru.practicum.shareit.booking.BookingStatus;
import ru.practicum.shareit.booking.dto.BookingShortDto;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.exception.ValidationException;
import ru.practicum.shareit.item.dto.CommentDto;
import ru.practicum.shareit.item.dto.CommentRequestDto;
import ru.practicum.shareit.item.dto.ItemDetailsDto;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.dto.ItemWithBookingsDto;
import ru.practicum.shareit.item.model.Comment;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.user.User;
import ru.practicum.shareit.user.UserRepository;
import ru.practicum.shareit.validation.OnUpdate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemServiceImpl implements ItemService {
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final BookingRepository bookingRepository;
    private final CommentRepository commentRepository;
    private final Validator validator;
    private final Clock clock;

    @Override
    @Transactional
    public ItemDto create(long ownerId, ItemDto dto) {
        User owner = userRepository.findForUpdate(ownerId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        validate(dto);
        Item item = ItemMapper.toModel(dto, owner);
        item.setId(null);
        return ItemMapper.toDto(itemRepository.save(item));
    }

    @Override
    @Transactional
    public ItemDto update(long ownerId, long itemId, ItemDto dto) {
        Item current = itemRepository.findForUpdate(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        if (current.getOwner().getId() != ownerId) {
            throw new NotFoundException("Item not found");
        }

        validate(dto, OnUpdate.class);
        current.setName(Objects.requireNonNullElse(dto.getName(), current.getName()));
        current.setDescription(Objects.requireNonNullElse(dto.getDescription(), current.getDescription()));
        current.setAvailable(Objects.requireNonNullElse(dto.getAvailable(), current.getAvailable()));
        return ItemMapper.toDto(current);
    }

    @Override
    public ItemDetailsDto getById(long itemId, Long viewerId) {
        Item item = findItem(itemId);
        if (item.getOwner().getId().equals(viewerId)) {
            return toDetailsWithBookings(List.of(item)).getFirst();
        }
        List<CommentDto> comments = findComments(List.of(itemId)).getOrDefault(itemId, List.of());
        return ItemMapper.toDetails(item, comments);
    }

    @Override
    public List<ItemWithBookingsDto> getAllByOwner(long ownerId) {
        if (!userRepository.existsById(ownerId)) {
            throw new NotFoundException("User not found");
        }
        return toDetailsWithBookings(itemRepository.findAllByOwnerIdOrderById(ownerId));
    }

    @Override
    @Transactional
    public CommentDto addComment(long authorId, long itemId, CommentRequestDto dto) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Item item = findItem(itemId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!bookingRepository.existsByItemIdAndBookerIdAndStatusAndEndLessThanEqual(
                itemId, authorId, BookingStatus.APPROVED, now)) {
            throw new ValidationException("A completed approved booking is required to leave a comment");
        }
        return CommentMapper.toDto(commentRepository.save(new Comment(null, dto.text(), item, author, now)));
    }

    @Override
    public List<ItemDto> search(String text) {
        if (text.isBlank()) {
            return List.of();
        }

        return itemRepository.searchAvailable(text).stream()
                .map(ItemMapper::toDto)
                .toList();
    }

    private List<ItemWithBookingsDto> toDetailsWithBookings(List<Item> items) {
        if (items.isEmpty()) {
            return List.of();
        }

        List<Long> itemIds = items.stream().map(Item::getId).toList();
        Map<Long, List<CommentDto>> comments = findComments(itemIds);

        Map<Long, BookingShortDto> last = new HashMap<>();
        Map<Long, BookingShortDto> next = new HashMap<>();
        LocalDateTime now = LocalDateTime.now(clock);
        for (Booking booking : bookingRepository.findNearestApprovedBookings(itemIds, now)) {
            Long itemId = booking.getItem().getId();
            if (booking.getStart().isAfter(now)) {
                next.put(itemId, BookingMapper.toShortDto(booking));
            } else {
                last.put(itemId, BookingMapper.toShortDto(booking));
            }
        }
        return items.stream()
                .map(item -> ItemMapper.toDetails(item, last.get(item.getId()), next.get(item.getId()),
                        comments.getOrDefault(item.getId(), List.of())))
                .toList();
    }

    private Map<Long, List<CommentDto>> findComments(List<Long> itemIds) {
        Map<Long, List<CommentDto>> comments = new HashMap<>();
        for (Comment comment : commentRepository.findAllByItemIdInOrderByCreatedAscIdAsc(itemIds)) {
            comments.computeIfAbsent(comment.getItem().getId(), id -> new ArrayList<>())
                    .add(CommentMapper.toDto(comment));
        }
        return comments;
    }

    private Item findItem(long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
    }

    private void validate(ItemDto dto, Class<?>... groups) {
        Set<ConstraintViolation<ItemDto>> violations = validator.validate(dto, groups);
        if (!violations.isEmpty()) {
            ConstraintViolation<ItemDto> violation = violations.iterator().next();
            throw new ValidationException(violation.getPropertyPath() + " " + violation.getMessage());
        }
    }
}
