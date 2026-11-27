package ru.practicum.shareit.booking;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    @Query("select b.item.id from Booking b where b.id = :id")
    Optional<Long> findItemId(long id);

    @EntityGraph(attributePaths = "item")
    @Query("""
            select b from Booking b
            where ((:owner = true and b.item.owner.id = :userId)
                or (:owner = false and b.booker.id = :userId))
              and (:state = 'ALL'
                or (:state = 'CURRENT' and b.start <= :now and b.end > :now)
                or (:state = 'PAST' and b.end <= :now)
                or (:state = 'FUTURE' and b.start > :now)
                or (:state = 'WAITING' and b.status = ru.practicum.shareit.booking.BookingStatus.WAITING)
                or (:state = 'REJECTED' and b.status = ru.practicum.shareit.booking.BookingStatus.REJECTED))
            order by b.start desc, b.id desc
            """)
    List<Booking> findForUser(long userId, boolean owner, String state, LocalDateTime now);

    @Query(value = """
            select b.* from items i
            join bookings b on b.id in (
                (select previous.id from bookings previous
                 where previous.item_id = i.id and previous.status = 'APPROVED' and previous.start_date <= :now
                 order by previous.start_date desc, previous.id desc limit 1),
                (select upcoming.id from bookings upcoming
                 where upcoming.item_id = i.id and upcoming.status = 'APPROVED' and upcoming.start_date > :now
                 order by upcoming.start_date, upcoming.id limit 1))
            where i.id in (:itemIds)
            """, nativeQuery = true)
    List<Booking> findNearestApprovedBookings(Collection<Long> itemIds, LocalDateTime now);

    boolean existsByItemIdAndBookerIdAndStatusAndEndLessThanEqual(
            long itemId, long bookerId, BookingStatus status, LocalDateTime end);

    boolean existsByItemIdAndStatusAndStartLessThanAndEndGreaterThan(
            long itemId, BookingStatus status, LocalDateTime end, LocalDateTime start);
}
