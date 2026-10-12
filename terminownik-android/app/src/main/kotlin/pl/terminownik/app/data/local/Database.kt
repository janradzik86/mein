package pl.terminownik.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ItemEntity::class,
        BarcodeNameEntity::class,
        ShoppingItemEntity::class,
        ReminderLogEntity::class,
        ScheduledAlarmEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TerminownikDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao
    abstract fun barcodeNameDao(): BarcodeNameDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun reminderLogDao(): ReminderLogDao
    abstract fun scheduledAlarmDao(): ScheduledAlarmDao

    companion object {
        const val NAME: String = "terminownik.db"

        @Volatile
        private var instance: TerminownikDatabase? = null

        fun get(context: Context): TerminownikDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TerminownikDatabase::class.java,
                    NAME,
                )
                    // Wszystkie dane są lokalne i odtwarzalne z kopii użytkownika;
                    // przy zmianie schematu w kolejnych wersjach dodamy migracje.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }

        /** Tylko dla testów instrumentowanych. */
        fun buildInMemory(context: Context): TerminownikDatabase =
            Room.inMemoryDatabaseBuilder(context, TerminownikDatabase::class.java)
                .allowMainThreadQueries()
                .build()

        /** Zamknięcie instancji – używane przy pełnym usunięciu danych użytkownika. */
        fun closeInstance() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}
