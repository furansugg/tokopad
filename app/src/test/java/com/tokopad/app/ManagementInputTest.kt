package com.tokopad.app

import org.junit.Test
import org.junit.Assert.assertEquals

class ManagementInputTest {
    @Test fun numbersRejectInvalidInput() = managementInputSelfCheck()
    @Test fun failuresUseSelectedLanguage() {
        assertEquals("Stok tidak cukup. Kurangi jumlah atau perbarui stok produk.", operationError(IllegalArgumentException("Insufficient stock: Rice"), "id"))
        assertEquals("Insufficient stock: Rice", operationError(IllegalArgumentException("Insufficient stock: Rice"), "en"))
    }
}
