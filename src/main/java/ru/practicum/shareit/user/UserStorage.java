package ru.practicum.shareit.user;

import java.util.List;
import java.util.Optional;

public interface UserStorage {
    User create(User user);

    User update(long userId, String name, String email);

    Optional<User> findById(long userId);

    Optional<User> findByEmail(String email);

    List<User> findAll();

    void delete(long userId);
}
