package com.tokopad.app

enum class ProductKind { PHYSICAL, PULSA, ELECTRICITY, DATA, VOUCHER, OTHER }

data class Product(
    val id: Long = 0,
    val name: String,
    val category: String = "",
    val price: Long,
    val cost: Long? = null,
    val kind: ProductKind = ProductKind.PHYSICAL,
    val unit: String = "pcs",
    val trackStock: Boolean = false,
    val stock: Long = 0,
    val provider: String = "",
    val packageName: String = "",
    val nominal: Long = 0,
    val requiresCode: Boolean = false,
    val active: Boolean = true,
) : java.io.Serializable

data class Customer(val id: Long = 0, val name: String, val phone: String = "", val note: String = "") : java.io.Serializable

data class CartLine(
    val product: Product,
    val quantity: Long = 1,
    val destination: String = "",
    val code: String = "",
    val note: String = "",
) : java.io.Serializable

data class ShopSettings(
    val language: String = "en",
    val shopName: String = "Tokopad",
    val address: String = "",
    val contact: String = "",
    val header: String = "",
    val footer: String = "Thank you!",
    val showCustomer: Boolean = true,
    val cashier: String = "",
    val showDiscount: Boolean = true,
    val paperWidth: Int = 58,
    val printerAddress: String = "",
)

data class Sale(
    val id: Long,
    val number: String,
    val createdAt: Long,
    val zoneId: String,
    val lines: List<CartLine>,
    val customerId: Long?,
    val customerName: String,
    val customerPhone: String,
    val subtotal: Long,
    val discount: Long,
    val total: Long,
    val payment: String,
    val paid: Long,
    val shop: ShopSettings,
    val cancelledAt: Long? = null,
    val cancellationReason: String = "",
)

data class Dashboard(val revenue: Long = 0, val count: Long = 0) {
    val average: Long get() = if (count == 0L) 0 else revenue / count
}
