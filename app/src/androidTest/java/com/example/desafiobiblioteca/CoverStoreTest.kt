package com.example.desafiobiblioteca

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CoverStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun importsPrivatelySurvivesSourceRemovalAndRejectsInvalidFiles() {
        val owner = "cover-test-${System.nanoTime()}"
        val store = CoverStore(context, owner)
        val source = coverFixture(context.cacheDir)
        var path: String? = null
        val invalid = File(context.cacheDir, "not-an-image.txt").apply { writeText("not an image") }
        try {
            path = store.importImage(Uri.fromFile(source))
            source.delete()
            assertTrue(store.file(path)!!.isFile)
            assertNotNull(CoverStore(context, owner).read(path))
            assertNull(CoverStore(context, "$owner-other").file(path))
            assertNull(store.file("../biblioteca.db"))
            try { store.importImage(Uri.fromFile(invalid)); fail("Invalid image accepted") } catch (_: IllegalArgumentException) { }
            store.remove(path)
            assertNull(store.file(path))
        } finally { store.remove(path); source.delete(); invalid.delete() }
    }
    @Test fun upgradesVersionOneWithoutLosingBooksGoalsOrProgress() {
        val name = "migration-${System.nanoTime()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val old = SQLiteDatabase.openOrCreateDatabase(file, null)
        old.execSQL("CREATE TABLE books(id INTEGER PRIMARY KEY AUTOINCREMENT,uid TEXT,title TEXT,author TEXT,genre TEXT,total INTEGER,pages_read INTEGER,state TEXT,rating INTEGER,finished_at INTEGER)")
        old.execSQL("CREATE TABLE goals(uid TEXT,year INTEGER,target INTEGER,PRIMARY KEY(uid,year))")
        old.execSQL("CREATE TABLE reading(uid TEXT,book_id INTEGER,year INTEGER,pages INTEGER,PRIMARY KEY(uid,book_id,year))")
        old.execSQL("INSERT INTO books VALUES(1,'owner','Libro anterior','Autora','Novela',100,20,'Leyendo',0,NULL)")
        old.execSQL("INSERT INTO goals VALUES('owner',2026,12)")
        old.execSQL("INSERT INTO reading VALUES('owner',1,2026,20)")
        old.version = 1; old.close()
        val source = coverFixture(context.cacheDir)
        val store = CoverStore(context,"owner")
        var path: String? = null
        var db = LibraryDb(context,name)
        try {
            assertEquals(2, db.readableDatabase.version)
            val book = db.books("owner").single()
            assertEquals("Libro anterior",book.title); assertNull(book.coverPath)
            assertEquals(12,db.goal("owner",2026)); assertEquals(20L,db.pages("owner",2026))
            path=store.importImage(Uri.fromFile(source))
            db.save("owner",book.copy(coverPath=path))
            db.close(); db=LibraryDb(context,name)
            assertEquals(path,db.books("owner").single().coverPath)
            assertTrue(db.books("other").isEmpty())
            db.save("owner",db.books("owner").single().copy(coverPath=null))
            assertNull(db.books("owner").single().coverPath)
        } finally { db.close(); context.deleteDatabase(name); store.remove(path); source.delete() }
    }
}
