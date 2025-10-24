package com.example.guerraentrevecinos.database;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.example.guerraentrevecinos.database.dao.*;
import com.example.guerraentrevecinos.database.entities.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {
                Player.class,
                Game.class,
                Unit.class,
                GameUnit.class,
                Move.class,
                PowerUsage.class,
                GameStats.class
        },
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    // DAOs
    public abstract PlayerDao playerDao();
    public abstract GameDao gameDao();
    public abstract UnitDao unitDao();
    public abstract GameUnitDao gameUnitDao();
    public abstract MoveDao moveDao();
    public abstract PowerUsageDao powerUsageDao();
    public abstract GameStatsDao gameStatsDao();

    // Singleton instance
    private static volatile AppDatabase INSTANCE;

    // Executor for database operations
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    // Get database instance
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "guerra_vecinos_database"
                            )
                            .addCallback(roomDatabaseCallback)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    // Callback to seed database on creation
    private static RoomDatabase.Callback roomDatabaseCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);

            // Seed initial data
            databaseWriteExecutor.execute(() -> {
                // Seed units
                UnitDao unitDao = INSTANCE.unitDao();

                unitDao.insert(new Unit("sunflower", "Sunflower", 2,
                        false, "Standard unit"));

                unitDao.insert(new Unit("rose", "Rose", 2,
                        true, "Changes color after being hit"));

                unitDao.insert(new Unit("dog", "Dog", 2,
                        true, "Fear: Can't be attacked twice in a row"));

                unitDao.insert(new Unit("cat", "Cat", 2,
                        true, "Teleports to random space after hit"));

                // Create default AI player
                PlayerDao playerDao = INSTANCE.playerDao();
                playerDao.insert(new Player("Computer", true));
            });
        }
    };
}