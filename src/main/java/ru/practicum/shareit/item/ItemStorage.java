package ru.practicum.shareit.item;

import ru.practicum.shareit.item.model.Item;

import java.util.List;
import java.util.Optional;

public interface ItemStorage {
    Item create(Item item);

    Item update(Item item);

    Optional<Item> findById(long itemId);

    List<Item> findAllByOwner(long ownerId);

    List<Item> searchAvailable(String text);

    void deleteAllByOwner(long ownerId);
}
