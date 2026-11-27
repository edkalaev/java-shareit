package ru.practicum.shareit.item;

import ru.practicum.shareit.item.dto.CommentDto;
import ru.practicum.shareit.item.dto.CommentRequestDto;
import ru.practicum.shareit.item.dto.ItemDetailsDto;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.dto.ItemWithBookingsDto;

import java.util.List;

public interface ItemService {
    ItemDto create(long ownerId, ItemDto dto);

    ItemDto update(long ownerId, long itemId, ItemDto dto);

    ItemDetailsDto getById(long itemId, Long viewerId);

    List<ItemWithBookingsDto> getAllByOwner(long ownerId);

    CommentDto addComment(long authorId, long itemId, CommentRequestDto dto);

    List<ItemDto> search(String text);
}
