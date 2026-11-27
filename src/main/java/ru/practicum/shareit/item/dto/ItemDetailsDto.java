package ru.practicum.shareit.item.dto;

import java.util.List;

public interface ItemDetailsDto {
    Long id();

    String name();

    String description();

    Boolean available();

    List<CommentDto> comments();
}
