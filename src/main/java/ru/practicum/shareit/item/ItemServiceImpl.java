package ru.practicum.shareit.item;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.exception.ValidationException;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.storage.InMemoryOperationLock;
import ru.practicum.shareit.user.User;
import ru.practicum.shareit.user.UserStorage;
import ru.practicum.shareit.validation.OnUpdate;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {
    private final UserStorage userStorage;
    private final ItemStorage itemStorage;
    private final Validator validator;
    private final InMemoryOperationLock operationLock;

    @Override
    public ItemDto create(long ownerId, ItemDto dto) {
        synchronized (operationLock) {
            User owner = findOwner(ownerId);
            validate(dto);
            Item item = ItemMapper.toModel(dto, owner);
            return ItemMapper.toDto(itemStorage.create(item));
        }
    }

    @Override
    public ItemDto update(long ownerId, long itemId, ItemDto dto) {
        synchronized (operationLock) {
            Item current = findItem(itemId);
            if (current.getOwner().getId() != ownerId) {
                throw new NotFoundException("Item not found");
            }

            validate(dto, OnUpdate.class);
            Item updated = new Item(
                    current.getId(),
                    Objects.requireNonNullElse(dto.getName(), current.getName()),
                    Objects.requireNonNullElse(dto.getDescription(), current.getDescription()),
                    Objects.requireNonNullElse(dto.getAvailable(), current.getAvailable()),
                    current.getOwner()
            );
            return ItemMapper.toDto(itemStorage.update(updated));
        }
    }

    @Override
    public ItemDto getById(long itemId) {
        synchronized (operationLock) {
            return ItemMapper.toDto(findItem(itemId));
        }
    }

    @Override
    public List<ItemDto> getAllByOwner(long ownerId) {
        synchronized (operationLock) {
            findOwner(ownerId);
            return itemStorage.findAllByOwner(ownerId).stream()
                    .map(ItemMapper::toDto)
                    .toList();
        }
    }

    @Override
    public List<ItemDto> search(String text) {
        if (text.isBlank()) {
            return List.of();
        }

        synchronized (operationLock) {
            return itemStorage.searchAvailable(text).stream()
                    .map(ItemMapper::toDto)
                    .toList();
        }
    }

    private User findOwner(long ownerId) {
        return userStorage.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private Item findItem(long itemId) {
        return itemStorage.findById(itemId)
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
