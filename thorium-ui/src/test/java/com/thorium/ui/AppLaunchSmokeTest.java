package com.thorium.ui;

import com.thorium.application.port.RoomRepository;
import com.thorium.domain.model.Room;
import com.thorium.domain.model.RoomType;
import com.thorium.infrastructure.ApplicationBootstrap;
import com.thorium.infrastructure.persistence.DatabaseInitializer;
import com.thorium.infrastructure.persistence.SQLiteConnectionProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppLaunchSmokeTest {

    @TempDir
    Path tempDir;

    @Test
    void bootstrapCreatesRoomRepository() {
        ApplicationBootstrap bootstrap = ApplicationBootstrap.create(tempDir.resolve("test.db"));
        RoomRepository repo = bootstrap.roomRepository();
        assertNotNull(repo);

        Room room = new Room(null, "R001", "Physics Lab", RoomType.LAB, 30);
        repo.save(room);
        assertNotNull(room.getId());

        Room saved = repo.findById(room.getId()).orElseThrow();
        assertEquals("R001", saved.getCode());
        assertEquals("Physics Lab", saved.getName());
        assertEquals(RoomType.LAB, saved.getType());
        assertEquals(30, saved.getCapacity());

        assertEquals(1, repo.count());
    }

    @Test
    void databaseInitializerCreatesRoomsTable() throws Exception {
        SQLiteConnectionProvider provider = new SQLiteConnectionProvider(tempDir.resolve("rooms.db"));
        DatabaseInitializer initializer = new DatabaseInitializer(provider);
        initializer.initialize();

        long count = provider.getConnection().createStatement()
                .executeQuery("SELECT COUNT(*) FROM rooms")
                .getLong(1);
        assertEquals(0, count, "Rooms table should exist and be empty");
    }
}
