package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Invoice::class, Account::class, Product::class, Voucher::class, ProductCategory::class, ExchangeRate::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        private val INSTANCES = java.util.concurrent.ConcurrentHashMap<String, AppDatabase>()

        fun getDatabase(context: Context, dbName: String = "smart_accountant_db"): AppDatabase {
            val actualDbName = if (dbName.endsWith(".db")) dbName else "$dbName.db"
            return INSTANCES.getOrPut(actualDbName) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    actualDbName
                )
                .fallbackToDestructiveMigration()
                .build()
            }
        }
    }
}
