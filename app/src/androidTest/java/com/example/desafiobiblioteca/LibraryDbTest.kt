package com.example.desafiobiblioteca

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class LibraryDbTest {
    @Test fun persistsIsolatesAndAccountsForAnnualProgress() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        var db = LibraryDb(ctx)
        val owner = "test-owner-${System.nanoTime()}"
        val other = "$owner-other"
        val now = System.currentTimeMillis()
        val year = BookRules.year(now)
        val previousYear = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.YEAR,-1) }.timeInMillis
        val first = Book(title="Prueba local",author="Autora",genre="Novela",total=200,read=50,state="Leyendo")
        try {
            val id = db.save(owner,first,previousYear)
            db.setGoal(owner,year,12)
            db.close(); db = LibraryDb(ctx)
            assertEquals(12, db.goal(owner,year))
            assertNull(db.goal(other,year))
            assertTrue(db.books(other).isEmpty())
            val saved = db.books(owner).single()
            assertEquals(50,saved.read)
            db.save(owner,saved.copy(read=100),now)
            assertEquals(50L,db.pages(owner,year))
            assertEquals(50L,db.pages(owner,year-1))
            db.save(owner,saved.copy(read=80),now)
            assertEquals(30L,db.pages(owner,year))
            db.save(owner,saved.copy(read=200,state="Terminado",rating=5),now)
            assertEquals(now,db.books(owner).single().finishedAt)
            db.save(owner,db.books(owner).single().copy(rating=4),now+10000)
            assertEquals(now,db.books(owner).single().finishedAt)
            try { db.save(other,first.copy(id=id)); fail("Cross-user update allowed") } catch (_: IllegalStateException) { }
            assertEquals("Prueba local",db.books(owner).single().title)
            db.save(owner,db.books(owner).single().copy(read=180,state="Leyendo",rating=0),now)
            assertNull(db.books(owner).single().finishedAt)
        } finally {
            listOf("books","goals","reading").forEach { db.writableDatabase.delete(it,"uid=?",arrayOf(owner)) }
            db.close()
        }
    }
}
