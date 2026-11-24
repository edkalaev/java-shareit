package ru.practicum.shareit.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.shareit.exception.ConflictException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.item.ItemStorage;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    // Both services use this monitor for operations involving users and their items.
    private final UserStorage userStorage;
    private final ItemStorage itemStorage;

    @Override
    public UserDto create(UserDto dto) {
        synchronized (userStorage) {
            checkEmailAvailable(dto.getEmail(), null);
            return UserMapper.toDto(userStorage.create(UserMapper.toModel(dto)));
        }
    }

    @Override
    public UserDto update(long userId, UserDto dto) {
        synchronized (userStorage) {
            User current = findUser(userId);
            String name = Objects.requireNonNullElse(dto.getName(), current.getName());
            String email = Objects.requireNonNullElse(dto.getEmail(), current.getEmail());

            if (dto.getEmail() != null) {
                checkEmailAvailable(email, userId);
            }
            return UserMapper.toDto(userStorage.update(userId, name, email));
        }
    }

    @Override
    public UserDto getById(long userId) {
        synchronized (userStorage) {
            return UserMapper.toDto(findUser(userId));
        }
    }

    @Override
    public List<UserDto> getAll() {
        synchronized (userStorage) {
            return userStorage.findAll().stream()
                    .map(UserMapper::toDto)
                    .toList();
        }
    }

    @Override
    public void delete(long userId) {
        synchronized (userStorage) {
            findUser(userId);
            itemStorage.deleteAllByOwner(userId);
            userStorage.delete(userId);
        }
    }

    private User findUser(long userId) {
        return userStorage.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private void checkEmailAvailable(String email, Long userId) {
        if (userStorage.findByEmail(email).filter(user -> !user.getId().equals(userId)).isPresent()) {
            throw new ConflictException("Email is already in use");
        }
    }
}
