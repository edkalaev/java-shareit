package ru.practicum.shareit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import ru.practicum.shareit.booking.Booking;
import ru.practicum.shareit.booking.BookingRepository;
import ru.practicum.shareit.booking.BookingState;
import ru.practicum.shareit.booking.BookingStatus;
import ru.practicum.shareit.item.ItemRepository;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.user.User;
import ru.practicum.shareit.user.UserRepository;
import ru.practicum.shareit.user.UserService;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(BookingAndCommentApiTests.FixedClockConfiguration.class)
class BookingAndCommentApiTests {
    private static final String USER_HEADER = "X-Sharer-User-Id";
    private static final LocalDateTime NOW = LocalDateTime.of(2028, 3, 10, 10, 0);
    private static final String COMMENT_BODY = "{\"text\":\"Works well\",\"authorName\":\"Fake\"}";

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final BookingRepository bookings;
    private final ItemRepository items;
    private final UserRepository users;
    private final UserService userService;
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    private User owner;
    private User renter;
    private User other;
    private Item drill;

    @Autowired
    BookingAndCommentApiTests(MockMvc mvc, ObjectMapper objectMapper, BookingRepository bookings,
                              ItemRepository items, UserRepository users, UserService userService,
                              DataSource dataSource, JdbcTemplate jdbc) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.bookings = bookings;
        this.items = items;
        this.users = users;
        this.userService = userService;
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void createFixtures() {
        owner = users.save(new User(null, "Owner", "owner@fixtures.example"));
        renter = users.save(new User(null, "Renter", "renter@fixtures.example"));
        other = users.save(new User(null, "Other", "other@fixtures.example"));
        drill = items.save(new Item(null, "Drill", "Power tool", true, owner));
    }

    @AfterEach
    void clearFixtures() {
        userService.getAll().forEach(user -> userService.delete(user.getId()));
    }

    @Test
    void createsWaitingBookingAndLimitsAccessToItsParties() throws Exception {
        MvcResult result = submitBooking(renter.getId(), drill.getId(), NOW.plusHours(2), NOW.plusHours(3))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.booker.id").value(renter.getId()))
                .andExpect(jsonPath("$.item.name").value("Drill")).andReturn();
        long id = responseId(result);

        for (User participant : List.of(owner, renter)) {
            mvc.perform(get("/bookings/{id}", id).header(USER_HEADER, participant.getId()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.item.id").value(drill.getId()));
        }
        mvc.perform(get("/bookings/{id}", id).header(USER_HEADER, other.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/bookings/{id}", id).header(USER_HEADER, -1)).andExpect(status().isNotFound());
        mvc.perform(get("/bookings/{id}", -1).header(USER_HEADER, owner.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/bookings/{id}", id)).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select status from bookings where id = ?", String.class, id))
                .isEqualTo("WAITING");
        assertThat(items.findById(drill.getId()).orElseThrow().getAvailable()).isTrue();
    }

    @Test
    void rejectsMissingUsersAndItemsAndDisallowsUnavailableOrOwnedItems() throws Exception {
        LocalDateTime start = NOW.plusHours(2);
        LocalDateTime end = NOW.plusHours(3);
        Item unavailable = items.save(new Item(null, "Saw", "Hand tool", false, owner));

        submitBooking(-1, drill.getId(), start, end).andExpect(status().isNotFound());
        submitBooking(renter.getId(), 999999L, start, end).andExpect(status().isNotFound());
        submitBooking(renter.getId(), unavailable.getId(), start, end).andExpect(status().isBadRequest());
        submitBooking(owner.getId(), drill.getId(), start, end).andExpect(status().isNotFound());
        submitBooking(renter.getId(), null, start, end).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({"null, 3", "2, null", "-1, 3", "0, 3", "2, 1", "2, 2"})
    void rejectsInvalidIntervals(String startOffset, String endOffset) throws Exception {
        submitBooking(renter.getId(), drill.getId(), offset(startOffset), offset(endOffset))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isString());
        assertThat(bookings.count()).isZero();
    }

    @Test
    void restrictsStatusChangesAndAllowsAdjacentApprovedBookings() throws Exception {
        Booking first = booking(drill, renter, 1, 3, BookingStatus.WAITING);
        Booking overlap = booking(drill, other, 2, 4, BookingStatus.WAITING);
        Booking adjacent = booking(drill, other, 3, 5, BookingStatus.WAITING);

        approval(renter.getId(), first.getId(), true).andExpect(status().isForbidden());
        approval(other.getId(), first.getId(), true).andExpect(status().isForbidden());
        approval(-1, first.getId(), true).andExpect(status().isForbidden());
        approval(owner.getId(), -1, true).andExpect(status().isNotFound());
        approval(owner.getId(), first.getId(), true)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        approval(owner.getId(), first.getId(), true).andExpect(status().isBadRequest());
        approval(owner.getId(), overlap.getId(), true).andExpect(status().isBadRequest());
        approval(owner.getId(), overlap.getId(), false)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        approval(owner.getId(), overlap.getId(), true).andExpect(status().isBadRequest());
        approval(owner.getId(), adjacent.getId(), true).andExpect(status().isOk());
    }

    @Test
    void approvesOnlyOneOfTwoConcurrentOverlappingBookings() throws Exception {
        Booking first = booking(drill, renter, 1, 3, BookingStatus.WAITING);
        Booking second = booking(drill, other, 2, 4, BookingStatus.WAITING);
        CyclicBarrier start = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstApproval = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return approval(owner.getId(), first.getId(), true).andReturn().getResponse().getStatus();
            });
            var secondApproval = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return approval(owner.getId(), second.getId(), true).andReturn().getResponse().getStatus();
            });
            assertThat(List.of(firstApproval.get(10, TimeUnit.SECONDS), secondApproval.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 400);
        }
    }

    @Test
    void doesNotApproveAnEndedBookingOrGrantCommentRightsRetroactively() throws Exception {
        Booking ended = booking(drill, renter, -2, 0, BookingStatus.WAITING);

        approval(owner.getId(), ended.getId(), true).andExpect(status().isBadRequest());
        assertThat(bookings.findById(ended.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.WAITING);
        submitComment(renter.getId(), drill.getId(), COMMENT_BODY).andExpect(status().isBadRequest());
        approval(owner.getId(), ended.getId(), false).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(BookingState.class)
    void filtersBothBookingListsWithConsistentBoundariesAndOrdering(BookingState state) throws Exception {
        Item saw = items.save(new Item(null, "Saw", "Hand tool", true, owner));
        Item unrelated = items.save(new Item(null, "Car", "City car", true, other));
        Booking future = booking(saw, renter, 2, 3, BookingStatus.WAITING);
        Booking current = booking(drill, renter, 0, 1, BookingStatus.APPROVED);
        Booking past = booking(drill, renter, -2, 0, BookingStatus.REJECTED);
        Booking otherBooker = booking(saw, other, 4, 5, BookingStatus.CANCELED);
        Booking otherOwner = booking(unrelated, renter, 6, 7, BookingStatus.CANCELED);

        List<Long> renterExpected = switch (state) {
            case ALL -> List.of(otherOwner.getId(), future.getId(), current.getId(), past.getId());
            case CURRENT -> List.of(current.getId());
            case PAST, REJECTED -> List.of(past.getId());
            case FUTURE -> List.of(otherOwner.getId(), future.getId());
            case WAITING -> List.of(future.getId());
        };
        List<Long> ownerExpected = switch (state) {
            case ALL -> List.of(otherBooker.getId(), future.getId(), current.getId(), past.getId());
            case CURRENT -> List.of(current.getId());
            case PAST, REJECTED -> List.of(past.getId());
            case FUTURE -> List.of(otherBooker.getId(), future.getId());
            case WAITING -> List.of(future.getId());
        };
        assertThat(listIds("/bookings", renter.getId(), state.name())).containsExactlyElementsOf(renterExpected);
        assertThat(listIds("/bookings/owner", owner.getId(), state.name())).containsExactlyElementsOf(ownerExpected);
    }

    @Test
    void handlesDefaultEmptyAndInvalidBookingFilters() throws Exception {
        MvcResult created = submitBooking(renter.getId(), drill.getId(), NOW.plusHours(2), NOW.plusHours(4))
                .andExpect(status().isOk()).andReturn();
        long id = responseId(created);
        assertThat(listIds("/bookings", renter.getId(), null)).containsExactly(id);
        assertThat(listIds("/bookings/owner", owner.getId(), null)).containsExactly(id);
        assertThat(listIds("/bookings", owner.getId(), null)).isEmpty();
        assertThat(listIds("/bookings/owner", renter.getId(), null)).isEmpty();
        mvc.perform(get("/bookings").header(USER_HEADER, -1)).andExpect(status().isNotFound());
        for (String path : List.of("/bookings", "/bookings/owner")) {
            mvc.perform(get(path).header(USER_HEADER, owner.getId()).param("state", "INVALID"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isString());
        }
    }

    @Test
    void showsNearestApprovedBookingsOnlyToTheOwner() throws Exception {
        Item withoutBookings = items.save(new Item(null, "Tarp", "Large sheet", true, owner));
        Item pastOnly = items.save(new Item(null, "Ladder", "Long ladder", true, owner));
        Item futureOnly = items.save(new Item(null, "Tent", "Two-person tent", true, owner));
        booking(drill, renter, -4, -3, BookingStatus.APPROVED);
        Booking current = booking(drill, renter, -1, 1, BookingStatus.APPROVED);
        Booking next = booking(drill, renter, 2, 3, BookingStatus.APPROVED);
        booking(drill, renter, 4, 5, BookingStatus.APPROVED);
        booking(drill, other, 1, 2, BookingStatus.WAITING);
        booking(drill, other, 0, 1, BookingStatus.REJECTED);
        Booking past = booking(pastOnly, renter, -3, -1, BookingStatus.APPROVED);
        Booking future = booking(futureOnly, renter, 1, 4, BookingStatus.APPROVED);

        assertThat(bookings.findNearestApprovedBookings(List.of(drill.getId(), withoutBookings.getId()), NOW))
                .extracting(Booking::getId)
                .containsExactlyInAnyOrder(current.getId(), next.getId());

        mvc.perform(get("/items").header(USER_HEADER, owner.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastBooking.id").value(current.getId()))
                .andExpect(jsonPath("$[0].nextBooking.id").value(next.getId()))
                .andExpect(jsonPath("$[0].nextBooking.bookerId").value(renter.getId()))
                .andExpect(jsonPath("$[1].id").value(withoutBookings.getId()))
                .andExpect(jsonPath("$[1].lastBooking").isEmpty()).andExpect(jsonPath("$[1].nextBooking").isEmpty())
                .andExpect(jsonPath("$[2].lastBooking.id").value(past.getId()))
                .andExpect(jsonPath("$[2].nextBooking").isEmpty())
                .andExpect(jsonPath("$[3].lastBooking").isEmpty())
                .andExpect(jsonPath("$[3].nextBooking.id").value(future.getId()));

        mvc.perform(get("/items/{id}", drill.getId()).header(USER_HEADER, owner.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lastBooking.id").value(current.getId()));
        mvc.perform(get("/items/{id}", drill.getId()).header(USER_HEADER, renter.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lastBooking").isEmpty())
                .andExpect(jsonPath("$.nextBooking").isEmpty());
        mvc.perform(get("/items/{id}", drill.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.lastBooking").isEmpty());
    }

    @Test
    void publishesCommentsAfterApprovedRentalAndGroupsThemByItem() throws Exception {
        Item saw = items.save(new Item(null, "Saw", "Hand tool", true, owner));
        booking(drill, renter, -2, 0, BookingStatus.APPROVED);

        submitComment(renter.getId(), drill.getId(), COMMENT_BODY)
                .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("Works well"))
                .andExpect(jsonPath("$.authorName").value("Renter"))
                .andExpect(jsonPath("$.created").value(NOW.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        submitComment(renter.getId(), drill.getId(), "{\"text\":\"Second review\"}").andExpect(status().isOk());

        mvc.perform(get("/items/{id}", drill.getId()).header(USER_HEADER, other.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comments[0].text").value("Works well"))
                .andExpect(jsonPath("$.comments[1].text").value("Second review"));
        mvc.perform(get("/items").header(USER_HEADER, owner.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].comments.length()").value(2))
                .andExpect(jsonPath("$[1].id").value(saw.getId()))
                .andExpect(jsonPath("$[1].comments").isArray()).andExpect(jsonPath("$[1].comments").isEmpty());
    }

    @Test
    void requiresACompletedApprovedRentalAndValidCommentText() throws Exception {
        submitComment(renter.getId(), drill.getId(), COMMENT_BODY).andExpect(status().isBadRequest());
        booking(drill, renter, 1, 2, BookingStatus.APPROVED);
        submitComment(renter.getId(), drill.getId(), COMMENT_BODY).andExpect(status().isBadRequest());
        booking(drill, renter, -1, 1, BookingStatus.APPROVED);
        booking(drill, renter, -3, -2, BookingStatus.REJECTED);
        booking(drill, renter, -5, -4, BookingStatus.WAITING);
        submitComment(renter.getId(), drill.getId(), COMMENT_BODY).andExpect(status().isBadRequest());

        booking(drill, renter, -3, -1, BookingStatus.APPROVED);
        for (String body : List.of("{}", "{\"text\":\"\"}", "{\"text\":\"  \"}", "{\"text\":\"\u2003\"}")) {
            submitComment(renter.getId(), drill.getId(), body).andExpect(status().isBadRequest());
        }
        submitComment(-1, drill.getId(), COMMENT_BODY).andExpect(status().isNotFound());
        submitComment(renter.getId(), -1, COMMENT_BODY).andExpect(status().isNotFound());
    }

    @Test
    void schemaCanBeReappliedAndEnforcesForeignKeysAndDateOrder() throws Exception {
        Booking booking = booking(drill, renter, -3, -1, BookingStatus.APPROVED);
        submitComment(renter.getId(), drill.getId(), COMMENT_BODY).andExpect(status().isOk());
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);

        assertThat(bookings.findById(booking.getId())).isPresent();
        assertThat(jdbc.queryForObject("select count(*) from comments where item_id = ?", Long.class, drill.getId()))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(
                "insert into users (name, email, email_key) values (?, ?, lower(?))",
                "Duplicate", "OWNER@fixtures.example", "OWNER@fixtures.example"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into items (name, description, is_available, owner_id) values ('test', 'test', true, -1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> booking(drill, renter, 2, 1, BookingStatus.WAITING))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletesDependentBookingsAndCommentsOfAUserAndTheirItems() throws Exception {
        Item saw = items.save(new Item(null, "Saw", "Hand tool", true, owner));
        booking(drill, renter, -3, -1, BookingStatus.APPROVED);
        booking(saw, other, -4, -2, BookingStatus.APPROVED);
        submitComment(renter.getId(), drill.getId(), COMMENT_BODY).andExpect(status().isOk());
        submitComment(other.getId(), saw.getId(), COMMENT_BODY).andExpect(status().isOk());

        mvc.perform(delete("/users/{id}", renter.getId())).andExpect(status().isOk());
        assertThat(bookings.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from comments", Long.class)).isEqualTo(1);
        mvc.perform(delete("/users/{id}", owner.getId())).andExpect(status().isOk());
        assertThat(bookings.count()).isZero();
        assertThat(items.count()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from comments", Long.class)).isZero();
        assertThat(users.existsById(other.getId())).isTrue();
    }

    private Booking booking(Item item, User renter, int start, int end, BookingStatus status) {
        return bookings.save(new Booking(null, NOW.plusHours(start), NOW.plusHours(end), item, renter, status));
    }

    private ResultActions submitBooking(long userId, Long itemId, LocalDateTime start, LocalDateTime end) throws Exception {
        var body = objectMapper.createObjectNode();
        body.put("itemId", itemId);
        body.put("start", start == null ? null : start.toString());
        body.put("end", end == null ? null : end.toString());
        return mvc.perform(post("/bookings").header(USER_HEADER, userId).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)));
    }

    private ResultActions approval(long userId, long bookingId, boolean approved) throws Exception {
        return mvc.perform(patch("/bookings/{id}", bookingId).header(USER_HEADER, userId)
                .param("approved", Boolean.toString(approved)));
    }

    private ResultActions submitComment(long userId, long itemId, String body) throws Exception {
        return mvc.perform(post("/items/{id}/comment", itemId).header(USER_HEADER, userId)
                .contentType(APPLICATION_JSON).content(body));
    }

    private List<Long> listIds(String path, long userId, String state) throws Exception {
        var request = get(path).header(USER_HEADER, userId);
        if (state != null) {
            request.param("state", state);
        }
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        var response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return StreamSupport.stream(response.spliterator(), false)
                .map(node -> node.get("id").asLong()).toList();
    }

    private long responseId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asLong();
    }

    private LocalDateTime offset(String value) {
        return "null".equals(value) ? null : NOW.plusHours(Long.parseLong(value));
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        }
    }
}
