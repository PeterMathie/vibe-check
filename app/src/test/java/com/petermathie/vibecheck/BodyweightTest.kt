package com.petermathie.vibecheck

import com.petermathie.vibecheck.domain.body.BodyweightUnit
import com.petermathie.vibecheck.domain.body.bodyweightInputError
import com.petermathie.vibecheck.domain.body.convertBodyweight
import com.petermathie.vibecheck.ui.copyBodyPhoto
import com.petermathie.vibecheck.ui.formatBodyweight
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BodyweightTest {
    @get:Rule val files = TemporaryFolder()

    @Test
    fun kilogramsAndPoundsDescribeTheSameWeightInBothDirections() {
        val kilograms = BodyweightUnit.KILOGRAMS
        val pounds = BodyweightUnit.POUNDS
        val converted = convertBodyweight(80.0, kilograms, pounds)
        assertEquals(176.369809744, converted, 1e-10)
        assertEquals("176.37", formatBodyweight(converted))
        assertEquals(80.0, convertBodyweight(converted, pounds, kilograms), 1e-10)
        assertEquals(80.0, convertBodyweight(176.369809744, pounds, kilograms), 1e-10)
        val precise = 78.126789123
        assertEquals(precise, convertBodyweight(precise, kilograms, kilograms), 0.0)
        assertEquals(precise, convertBodyweight(precise, pounds, pounds), 0.0)
    }

    @Test
    fun blankClearsButInvalidOrNonPositiveNumbersDoNotSave() {
        listOf("", " ", "78.126", " 80 ", "0.001").forEach { assertNull(bodyweightInputError(it)) }
        listOf(".", "-", "not a weight", "0", "-1", "NaN", "Infinity", "1e999").forEach {
            assertNotNull(it, bodyweightInputError(it))
        }
    }

    @Test
    fun unsupportedUnitsAndConversionOverflowAreExplicitErrors() {
        assertEquals(BodyweightUnit.POUNDS, BodyweightUnit.stored("LB"))
        assertThrows(IllegalArgumentException::class.java) { BodyweightUnit.stored("stone") }
        assertThrows(IllegalArgumentException::class.java) {
            convertBodyweight(Double.MAX_VALUE, BodyweightUnit.KILOGRAMS, BodyweightUnit.POUNDS)
        }
    }

    @Test
    fun photosStayOnTheCapturedLocalDayAndDoNotOverwriteOneAnother() {
        val directory = File(files.root, "photos")
        val day = LocalDate.of(2026, 11, 1)
        val zone = ZoneId.of("America/Toronto")
        val first = copyBodyPhoto(directory, day, zone) { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        val second = copyBodyPhoto(directory, day, zone) { ByteArrayInputStream(byteArrayOf(4, 5)) }
        assertNotEquals(first, second)
        assertArrayEquals(byteArrayOf(1, 2, 3), first.readBytes())
        assertArrayEquals(byteArrayOf(4, 5), second.readBytes())
        listOf(first, second).forEach {
            assertEquals(day, Instant.ofEpochMilli(it.nameWithoutExtension.toLong()).atZone(zone).toLocalDate())
        }
        assertTrue(directory.listFiles().orEmpty().all { it.extension == "jpg" })
    }

    @Test
    fun failedPhotoCopyDoesNotLeaveACompleteOrPartialPhoto() {
        val directory = File(files.root, "photos")
        val failure = IOException("Synthetic read failure")
        val thrown = assertThrows(IOException::class.java) {
            copyBodyPhoto(directory, LocalDate.of(2026, 11, 26), ZoneId.of("UTC")) {
                object : InputStream() {
                    override fun read(): Int = throw failure
                }
            }
        }
        assertSame(failure, thrown)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
}
