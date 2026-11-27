package ru.practicum.shareit.item;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import ru.practicum.shareit.item.model.Item;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {
    List<Item> findAllByOwnerIdOrderById(long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i where i.id = :id")
    Optional<Item> findForUpdate(long id);

    @Query("""
            select i from Item i
            where i.available = true
              and (locate(lower(:text), lower(i.name)) > 0
                or locate(lower(:text), lower(i.description)) > 0)
            order by i.id
            """)
    List<Item> searchAvailable(String text);
}
