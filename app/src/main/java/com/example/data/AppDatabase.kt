package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CompanyEntity::class,
        CurrencyEntity::class,
        AccountEntity::class,
        ProductEntity::class,
        InvoiceEntity::class,
        VoucherEntity::class,
        AttendanceEntity::class,
        ManufacturingEntity::class,
        WarehouseEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun companyDao(): CompanyDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun accountDao(): AccountDao
    abstract fun productDao(): ProductDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun voucherDao(): VoucherDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun manufacturingDao(): ManufacturingDao
    abstract fun warehouseDao(): WarehouseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_accountant_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
