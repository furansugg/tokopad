package com.tokopad.app

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class ManagementInputTest {
    @Test fun numbersRejectInvalidInput() = managementInputSelfCheck()
    @Test fun failuresUseSelectedLanguage() {
        assertEquals("Stok tidak cukup. Kurangi jumlah atau perbarui stok produk.", operationError(IllegalArgumentException("Insufficient stock: Rice"), "id"))
        assertEquals("Insufficient stock: Rice", operationError(IllegalArgumentException("Insufficient stock: Rice"), "en"))
    }
    @Test fun printerErrorsDistinguishConnectionAndUncertainWrite() {
        assertTrue(operationError(java.io.IOException("Cannot connect to printer using secure or insecure SPP"), "id").contains("Koneksi printer gagal"))
        assertTrue(operationError(java.io.IOException("Printer is Bluetooth LE-only; BLE printers are not supported"), "id").contains("BLE"))
        assertTrue(operationError(java.io.IOException("Printer write failed; receipt may be partially printed"), "en").contains("partially printed"))
    }
}
