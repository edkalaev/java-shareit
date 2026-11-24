package ru.practicum.shareit.item;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.model.Item;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ItemMapper {
    public static ItemDto toDto(Item item) {
        Long requestId = item.getRequest() == null ? null : item.getRequest().getId();
        return new ItemDto(item.getId(), item.getName(), item.getDescription(), item.getAvailable(), requestId);
    }

    public static Item toModel(ItemDto dto) {
        return new Item(dto.getId(), dto.getName(), dto.getDescription(), dto.getAvailable(), null, null);
    }
}
