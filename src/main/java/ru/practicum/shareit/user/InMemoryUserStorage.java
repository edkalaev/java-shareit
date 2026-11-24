package ru.practicum.shareit.user;

import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class InMemoryUserStorage implements UserStorage {
    private final Map<Long, User> users = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public synchronized User create(User user) {
        user.setId(nextId++);
        users.put(user.getId(), user);
        return user;
    }

    @Override
    public synchronized User update(long userId, String name, String email) {
        User user = users.get(userId);
        user.setName(name);
        user.setEmail(email);
        return user;
    }

    @Override
    public synchronized Optional<User> findById(long userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public synchronized Optional<User> findByEmail(String email) {
        return users.values().stream()
                .filter(user -> user.getEmail().equalsIgnoreCase(email))
                .findFirst();
    }

    @Override
    public synchronized List<User> findAll() {
        return List.copyOf(users.values());
    }

    @Override
    public synchronized void delete(long userId) {
        users.remove(userId);
    }
}
