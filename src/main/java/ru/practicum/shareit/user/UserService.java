package ru.practicum.shareit.user;

import java.util.List;

public interface UserService {
    UserDto create(UserDto dto);

    UserDto update(long userId, UserDto dto);

    UserDto getById(long userId);

    List<UserDto> getAll();

    void delete(long userId);
}
