package ru.practicum.shareit.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.shareit.exception.ConflictException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.item.ItemStorage;
import ru.practicum.shareit.storage.InMemoryOperationLock;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserStorage userStorage;
    private final ItemStorage itemStorage;
    private final InMemoryOperationLock operationLock;

    @Override
    public UserDto create(UserDto dto) {
        synchronized (operationLock) {
            checkEmailAvailable(dto.getEmail(), null);
            return UserMapper.toDto(userStorage.create(UserMapper.toModel(dto)));
        }
    }

    @Override
    public UserDto update(long userId, UserDto dto) {
        synchronized (operationLock) {
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
        synchronized (operationLock) {
            return UserMapper.toDto(findUser(userId));
        }
    }

    @Override
    public List<UserDto> getAll() {
        synchronized (operationLock) {
            return userStorage.findAll().stream()
                    .map(UserMapper::toDto)
                    .toList();
        }
    }

    @Override
    public void delete(long userId) {
        synchronized (operationLock) {
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
