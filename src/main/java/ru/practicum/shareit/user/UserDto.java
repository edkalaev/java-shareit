package ru.practicum.shareit.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.groups.Default;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.practicum.shareit.validation.OnUpdate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;

    @NotBlank
    @Pattern(regexp = OnUpdate.NON_BLANK, groups = {Default.class, OnUpdate.class}, message = "must not be blank")
    private String name;

    @NotBlank
    @Pattern(regexp = OnUpdate.NON_BLANK, groups = OnUpdate.class, message = "must not be blank")
    @Email(groups = {Default.class, OnUpdate.class})
    private String email;
}
