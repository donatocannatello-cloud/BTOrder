package it.example.ripassofoto.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PaginaStudio::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun paginaStudioDao(): PaginaStudioDao

    companion object {
        @Volatile
        private var istanza: AppDatabase? = null

        fun ottieni(context: Context): AppDatabase =
            istanza ?: synchronized(this) {
                istanza ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ripassofoto.db"
                ).build().also { istanza = it }
            }
    }
}
