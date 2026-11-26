package ru.practicum.shareit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.practicum.shareit.user.UserService;

import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ShareItApiTests {
    private static final String USER_ID_HEADER = "X-Sharer-User-Id";

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final UserService userService;

    @Autowired
    ShareItApiTests(MockMvc mvc, ObjectMapper objectMapper, UserService userService) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.userService = userService;
    }

    @AfterEach
    void clearUsersAndItems() {
        userService.getAll().forEach(user -> userService.delete(user.getId()));
    }

    @Test
    void createsUpdatesListsAndDeletesUsersWithServerGeneratedIds() throws Exception {
        mvc.perform(get("/users")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        long userId = createUser("First", "first@example.com");
        long otherId = createUser("Second", "second@example.com");
        mvc.perform(patch("/users/{id}", userId).contentType(APPLICATION_JSON)
                        .content("{\"id\":-2,\"name\":\"Changed\",\"email\":\"first@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.name").value("Changed"));
        mvc.perform(patch("/users/{id}", userId).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Changed"));
        mvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("new@example.com"))
                .andExpect(jsonPath("$[1].id").value(otherId));

        mvc.perform(delete("/users/{id}", otherId)).andExpect(status().isOk());
        mvc.perform(get("/users/{id}", otherId)).andExpect(status().isNotFound());
        assertThat(createUser("Third", "third@example.com")).isGreaterThan(otherId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\" \",\"email\":\"ok@example.com\"}",
            "{\"name\":\"\u2003\",\"email\":\"ok@example.com\"}",
            "{\"name\":\"Name\",\"email\":\"invalid\"}", "{\"name\":\"Name\",\"email\":\"\"}"})
    void rejectsInvalidUserCreation(String body) throws Exception {
        mvc.perform(post("/users").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void leavesUserUnchangedWhenPatchIsInvalidOrEmailIsTaken() throws Exception {
        long userId = createUser("First", "first@example.com");
        createUser("Second", "second@example.com");

        mvc.perform(patch("/users/{id}", userId).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Changed\",\"email\":\"SECOND@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").isString());
        mvc.perform(patch("/users/{id}", userId).contentType(APPLICATION_JSON)
                        .content("{\"name\":\" \",\"email\":\"new@example.com\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/users/{id}", userId).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("First"))
                .andExpect(jsonPath("$.email").value("first@example.com"));
        mvc.perform(patch("/users/{id}", -1).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void acceptsOnlyOneConcurrentRegistrationWithSameEmail() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Integer> register = () -> {
            start.await(5, TimeUnit.SECONDS);
            return mvc.perform(post("/users").contentType(APPLICATION_JSON)
                            .content("{\"name\":\"Same email\",\"email\":\"shared@example.com\"}"))
                    .andReturn().getResponse().getStatus();
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(register);
            var second = executor.submit(register);
            assertThat(new Integer[]{first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(200, 409);
        }

        mvc.perform(get("/users")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void allowsOnlyOwnerToPatchAndReturnsUnavailableItem() throws Exception {
        long ownerId = createUser("Owner", "owner@example.com");
        long otherId = createUser("Other", "other@example.com");
        long itemId = createItem(ownerId, "Drill", "Power tool", true);

        mvc.perform(patch("/items/{id}", itemId).header(USER_ID_HEADER, otherId)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/items/{id}", itemId).header(USER_ID_HEADER, ownerId)
                        .contentType(APPLICATION_JSON).content("{\"id\":-2,\"owner\":{\"id\":-1},\"requestId\":1,\"available\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId))
                .andExpect(jsonPath("$.name").value("Drill"))
                .andExpect(jsonPath("$.description").value("Power tool"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.requestId").doesNotHaveJsonPath());
        mvc.perform(patch("/items/{id}", itemId).header(USER_ID_HEADER, ownerId)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Changed\",\"description\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/items/{id}", itemId).header(USER_ID_HEADER, otherId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Drill"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.owner").doesNotExist());
    }

    @Test
    void listsOnlyOwnerItemsAndSearchesAvailableItemsAcrossOwners() throws Exception {
        long ownerId = createUser("Owner", "owner@example.com");
        long otherId = createUser("Other", "other@example.com");
        mvc.perform(get("/items").header(USER_ID_HEADER, ownerId))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        long drillId = createItem(ownerId, "Electric DRILL", "Power tool", true);
        long unavailableId = createItem(ownerId, "Drill", "Unavailable tool", false);
        long bitsId = createItem(otherId, "Bits", "Fits a drill", true);
        mvc.perform(get("/items").header(USER_ID_HEADER, ownerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(drillId))
                .andExpect(jsonPath("$[1].id").value(unavailableId));
        mvc.perform(get("/items/search").param("text", "rIl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(drillId))
                .andExpect(jsonPath("$[1].id").value(bitsId));
        for (String text : new String[]{"", "  ", "no matches"}) {
            mvc.perform(get("/items/search").param("text", text))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Test
    void deletesOnlyItemsOfRemovedOwner() throws Exception {
        long ownerId = createUser("Owner", "owner@example.com");
        long otherId = createUser("Other", "other@example.com");
        long itemId = createItem(ownerId, "Camera", "Digital camera", true);
        long otherItemId = createItem(otherId, "Tripod", "For a camera", true);

        mvc.perform(delete("/users/{id}", ownerId)).andExpect(status().isOk());
        mvc.perform(get("/items/{id}", itemId)).andExpect(status().isNotFound());
        mvc.perform(get("/items").header(USER_ID_HEADER, ownerId)).andExpect(status().isNotFound());
        mvc.perform(get("/items/search").param("text", "camera"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(otherItemId));
        assertThat(createItem(otherId, "Spare", "Replacement", false)).isGreaterThan(otherItemId);
    }

    @Test
    void concurrentItemCreationAndOwnerDeletionLeaveNoOrphanedItems() throws Exception {
        long ownerId = createUser("Owner", "owner@example.com");
        CyclicBarrier start = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var creation = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return mvc.perform(post("/items").header(USER_ID_HEADER, ownerId).contentType(APPLICATION_JSON)
                                .content("{\"name\":\"Camera\",\"description\":\"Digital camera\",\"available\":true}"))
                        .andReturn().getResponse().getStatus();
            });
            var deletion = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return mvc.perform(delete("/users/{id}", ownerId)).andReturn().getResponse().getStatus();
            });

            assertThat(deletion.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(creation.get(10, TimeUnit.SECONDS)).isIn(200, 404);
        }

        mvc.perform(get("/items/search").param("text", "camera"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\" \",\"description\":\"Description\",\"available\":true}",
            "{\"name\":\"Tool\",\"description\":\"\",\"available\":true}",
            "{\"name\":\"Tool\",\"description\":\"\u2003\",\"available\":true}",
            "{\"name\":\"Tool\",\"description\":\"Description\"}"})
    void rejectsInvalidItemCreation(String body) throws Exception {
        long ownerId = createUser("Owner", "owner@example.com");

        mvc.perform(post("/items").header(USER_ID_HEADER, ownerId).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void preservesFrameworkClientErrorStatuses() throws Exception {
        mvc.perform(get("/items")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required header X-Sharer-User-Id"));
        mvc.perform(get("/items").header(USER_ID_HEADER, "invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/items/invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/items/search")).andExpect(status().isBadRequest());
        mvc.perform(post("/users").contentType(APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/items").header(USER_ID_HEADER, -1).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/users")).andExpect(status().isMethodNotAllowed());
    }

    private long createUser(String name, String email) throws Exception {
        MvcResult result = mvc.perform(post("/users").contentType(APPLICATION_JSON)
                        .content("{\"id\":-1,\"name\":\"%s\",\"email\":\"%s\"}".formatted(name, email)))
                .andExpect(status().isOk())
                .andReturn();
        long userId = responseId(result);
        assertThat(userId).isPositive();
        return userId;
    }

    private long createItem(long ownerId, String name, String description, boolean available) throws Exception {
        MvcResult result = mvc.perform(post("/items").header(USER_ID_HEADER, ownerId).contentType(APPLICATION_JSON)
                        .content("{\"id\":-1,\"owner\":{\"id\":-1},\"name\":\"%s\",\"description\":\"%s\",\"available\":%s}".formatted(name, description, available)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(available))
                .andReturn();
        long itemId = responseId(result);
        assertThat(itemId).isPositive();
        return itemId;
    }

    private long responseId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asLong();
    }
}
