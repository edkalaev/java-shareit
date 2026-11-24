package ru.practicum.shareit.item;

import org.springframework.stereotype.Repository;
import ru.practicum.shareit.item.model.Item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Repository
public class InMemoryItemStorage implements ItemStorage {
    private final Map<Long, Item> items = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public synchronized Item create(Item item) {
        item.setId(nextId++);
        items.put(item.getId(), item);
        return item;
    }

    @Override
    public synchronized Item update(Item item) {
        items.put(item.getId(), item);
        return item;
    }

    @Override
    public synchronized Optional<Item> findById(long itemId) {
        return Optional.ofNullable(items.get(itemId));
    }

    @Override
    public synchronized List<Item> findAllByOwner(long ownerId) {
        return items.values().stream()
                .filter(item -> item.getOwner().getId() == ownerId)
                .toList();
    }

    @Override
    public synchronized List<Item> searchAvailable(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return items.values().stream()
                .filter(item -> Boolean.TRUE.equals(item.getAvailable()))
                .filter(item -> item.getName().toLowerCase(Locale.ROOT).contains(normalized)
                        || item.getDescription().toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
    }

    @Override
    public synchronized void deleteAllByOwner(long ownerId) {
        items.values().removeIf(item -> item.getOwner().getId() == ownerId);
    }
}
