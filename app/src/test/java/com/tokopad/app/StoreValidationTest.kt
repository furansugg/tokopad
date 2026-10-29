package com.tokopad.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

class StoreValidationTest {
    private fun settings() = JSONObject("""{"language":"en","shopName":"Shop","address":"","contact":"","header":"","footer":"Thanks","showCustomer":true,"cashier":"","showDiscount":true,"paperWidth":58,"printerAddress":""}""")

    private fun product() = JSONObject("""{"id":1,"name":"Rice","category":"","price":100,"cost":null,"kind":"PHYSICAL","unit":"pcs","trackStock":true,"stock":10,"provider":"","packageName":"","nominal":0,"requiresCode":false,"active":true}""")

    private fun sale() = JSONObject("""{"id":1,"number":"123e4567-e89b-42d3-a456-426614174000","createdAt":1000,"zoneId":"UTC","customerId":null,"customerName":"","customerPhone":"","subtotal":200,"discount":10,"total":190,"payment":"cash","paid":200,"cancelledAt":null,"cancellationReason":""}""")
        .put("shop", settings()).put("lines", JSONArray().put(JSONObject()
            .put("product", product()).put("quantity", 2).put("destination", "").put("code", "").put("note", "")))

    private fun data() = JSONObject().put("products", JSONArray().put(product()))
        .put("customers", JSONArray()).put("sales", JSONArray().put(sale())).put("settings", settings())

    private fun backup(data: JSONObject): String {
        val payload = data.toString()
        val checksum = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return JSONObject().put("version", 1).put("sha256", checksum).put("payload", payload).toString()
    }

    private fun rejects(data: JSONObject) { assertThrows(IllegalArgumentException::class.java) { Store.validateBackup(backup(data)) } }

    @Test fun acceptsValidSnapshotsIncludingDetachedCustomersAndInactiveProducts() {
        Store.validateBackup(backup(data()))
        val data = data()
        data.getJSONArray("products").getJSONObject(0).put("active", false).put("price", 300)
        data.getJSONArray("sales").getJSONObject(0).put("customerName", "Deleted customer")
            .put("cancelledAt", 2000).put("cancellationReason", "Correction")
        Store.validateBackup(backup(data))
        data.getJSONArray("sales").getJSONObject(0).put("payment", "noncash").put("paid", 190)
        Store.validateBackup(backup(data))
    }

    @Test fun rejectsTamperingAndUnsupportedVersion() {
        val envelope = JSONObject(backup(data())).put("sha256", "0".repeat(64))
        assertThrows(IllegalArgumentException::class.java) { Store.validateBackup(envelope.toString()) }
        envelope.put("version", 2)
        assertThrows(IllegalArgumentException::class.java) { Store.validateBackup(envelope.toString()) }
        assertThrows(IllegalArgumentException::class.java) { Store.validateBackup("not json") }
        assertThrows(IllegalArgumentException::class.java) { Store.validateBackup("[".repeat(1000) + "]".repeat(1000)) }
    }

    @Test fun backupLimitUsesUtf8BytesNotCharacterCount() {
        val text = "界".repeat(Store.MAX_BACKUP_BYTES / 3 + 1)
        val error = assertThrows(IllegalArgumentException::class.java) { Store.validateBackup(text) }
        assertEquals("Backup exceeds supported size", error.message)
    }

    @Test fun rejectsCoercedNumbersAndOverflow() {
        for (bad in listOf<Any>("100", 1.5, -1L, Long.MAX_VALUE)) {
            val data = data()
            data.getJSONArray("products").getJSONObject(0).put("price", bad)
            rejects(data)
        }
        val data = data()
        data.getJSONArray("sales").getJSONObject(0).getJSONArray("lines").getJSONObject(0)
            .put("quantity", 1_000_000_000L).getJSONObject("product").put("price", 1_000_000_000_000L)
        rejects(data)
        val excessiveTotal = data()
        val lines = excessiveTotal.getJSONArray("sales").getJSONObject(0).getJSONArray("lines")
        val first = lines.getJSONObject(0).put("quantity", 1)
        first.getJSONObject("product").put("price", 1_000_000_000_000L)
        lines.put(JSONObject(first.toString()))
        rejects(excessiveTotal)
    }

    @Test fun rejectsBrokenReferencesAndDuplicates() {
        rejects(data().put("products", JSONArray()))
        val data = data()
        data.getJSONArray("sales").getJSONObject(0).put("customerId", 12).put("customerName", "Missing")
        rejects(data)
        rejects(data().also { it.getJSONArray("products").put(product()) })
        rejects(data().also { it.getJSONArray("sales").put(sale()) })
    }

    @Test fun rejectsInvalidTotalsPaymentQuantityAndCancellation() {
        for ((key, value) in listOf("total" to 191, "discount" to 201, "paid" to 10, "cancelledAt" to 500)) {
            val data = data()
            data.getJSONArray("sales").getJSONObject(0).put(key, value)
            rejects(data)
        }
        val data = data()
        data.getJSONArray("sales").getJSONObject(0).getJSONArray("lines").getJSONObject(0).put("quantity", 0)
        rejects(data)
    }

    @Test fun rejectsMissingDigitalFieldsAndUnboundedText() {
        val data = data()
        data.getJSONArray("sales").getJSONObject(0).getJSONArray("lines").getJSONObject(0)
            .getJSONObject("product").put("kind", "ELECTRICITY")
        rejects(data)
        rejects(data().also { it.getJSONArray("products").getJSONObject(0).put("name", "x".repeat(201)) })
        rejects(data().also { it.getJSONObject("settings").put("paperWidth", 60) })
    }
}
