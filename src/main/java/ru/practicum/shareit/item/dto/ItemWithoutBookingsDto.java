package ru.practicum.shareit.item.dto;

import java.util.List;

public record ItemWithoutBookingsDto(Long id, String name, String description, Boolean available,
                                     List<CommentDto> comments) implements ItemDetailsDto {
}
