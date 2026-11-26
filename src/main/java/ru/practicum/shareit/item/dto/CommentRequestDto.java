package ru.practicum.shareit.item.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import ru.practicum.shareit.validation.OnUpdate;

public record CommentRequestDto(
        @NotBlank @Pattern(regexp = OnUpdate.NON_BLANK, message = "must not be blank") String text) {
}
