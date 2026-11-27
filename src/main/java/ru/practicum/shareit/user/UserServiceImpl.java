package ru.practicum.shareit.user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.exception.ConflictException;
import ru.practicum.shareit.exception.NotFoundException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserDto create(UserDto dto) {
        User user = UserMapper.toModel(dto);
        user.setId(null);
        checkEmailAvailable(user.getEmail(), null);
        return UserMapper.toDto(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserDto update(long userId, UserDto dto) {
        User user = findUserForUpdate(userId);
        if (dto.getEmail() != null) {
            checkEmailAvailable(dto.getEmail(), userId);
            user.setEmail(dto.getEmail());
        }
        if (dto.getName() != null) {
            user.setName(dto.getName());
        }
        return UserMapper.toDto(user);
    }

    @Override
    public UserDto getById(long userId) {
        return userRepository.findById(userId).map(UserMapper::toDto)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Override
    public List<UserDto> getAll() {
        return userRepository.findAll(Sort.by("id")).stream().map(UserMapper::toDto).toList();
    }

    @Override
    @Transactional
    public void delete(long userId) {
        userRepository.delete(findUserForUpdate(userId));
    }

    private User findUserForUpdate(long userId) {
        return userRepository.findForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private void checkEmailAvailable(String email, Long userId) {
        if (userRepository.findByEmailIgnoreCase(email).filter(user -> !user.getId().equals(userId)).isPresent()) {
            throw new ConflictException("Email is already in use");
        }
    }
}
