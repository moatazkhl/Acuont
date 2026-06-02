package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "companies")
@Serializable
data class CompanyEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val currencyLocal: String = "SYP" // Local base currency
)

@Entity(tableName = "currencies")
@Serializable
data class CurrencyEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val companyId: Int,
    val code: String,       // e.g. "USD"
    val name: String,       // e.g. "دولار أمريكي"
    val rateToLocal: Double // Exchange rate, e.g. 15000.0 (meaning 1 USD = 15000 SYP)
)

@Entity(tableName = "accounts")
@Serializable
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val companyId: Int,
    val name: String,
    val type: String,        // "زبون" (Client), "مورد" (Supplier), "مصاريف" (Expenses), "ايرادات" (Revenue)
    val currencyCode: String, // The default/active currency for this account
    val phone: String = "",
    val notes: String = ""
)

@Entity(tableName = "warehouses")
@Serializable
data class WarehouseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val companyId: Int,
    val name: String,
    val location: String = ""
)

@Entity(tableName = "products")
@Serializable
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val companyId: Int,
    val name: String,
    val category: String = "عام",
    val unit: String = "قطعة",
    val costPrice: Double = 0.0,    // Price in priceCurrency
    val sellingPrice: Double = 0.0, // Price in priceCurrency
    val barcode: String = "",
    val stockQuantity: Double = 0.0,
    val lowStockThreshold: Double = 5.0,
    val priceCurrency: String = "SYP",
    val warehouseName: String = "المستودع الرئيسي"
)

@Entity(tableName = "invoices")
@Serializable
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val companyId: Int,
    val invoiceNumber: Int,
    val type: String,              // "مبيع" (Sales), "شراء" (Purchase), "مردود مبيعات", "مردود مشتريات", "إتلاف"
    val date: Long = System.currentTimeMillis(),
    val accountId: Int,            // Selected client or supplier
    val currencyCode: String,      // Selected currency (e.g. SYP, USD)
    val exchangeRate: Double,      // Exchange rate applied at invoice date
    val discount: Double = 0.0,
    val totalAmountLocal: Double,  // Total amount calculated in local currency
    val totalAmountForeign: Double,// Total amount calculated in foreign currency
    val detailsJson: String        // JSON string serialized of List<InvoiceItem>
)

@Entity(tableName = "vouchers")
@Serializable
data class VoucherEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val companyId: Int,
    val type: String,              // "قبض" (Receipt), "دفع" (Payment)
    val date: Long = System.currentTimeMillis(),
    val accountId: Int,            // Associated account
    val amountForeign: Double,
    val currencyCode: String,
    val exchangeRate: Double,
    val amountLocal: Double,       // Converted value
    val notes: String = ""
)

@Entity(tableName = "attendance")
@Serializable
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val companyId: Int,
    val employeeName: String,
    val date: String,             // Format "yyyy-MM-dd"
    val status: String,           // "حاضر", "غائب", "إجازة"
    val notes: String = ""
)

@Entity(tableName = "manufacturing")
@Serializable
data class ManufacturingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val companyId: Int,
    val date: Long = System.currentTimeMillis(),
    val itemName: String,         // Rendered product name
    val quantityResult: Double,
    val rawMaterialsJson: String, // JSON serialized List<RawMaterial>
    val additionalCosts: Double,
    val totalCostLocal: Double
)

// Helper structures for serialization in JSON fields
@Serializable
data class InvoiceItem(
    val name: String,
    val quantity: Double,
    val unitPrice: Double // Stored in the invoice currency
)

@Serializable
data class RawMaterial(
    val name: String,
    val quantity: Double,
    val unitPrice: Double // Cost of material per unit
)
