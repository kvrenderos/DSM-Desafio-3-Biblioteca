package com.example.desafiobiblioteca

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Every query is scoped to the UID from FirebaseAuth, never an email or form input. */
class LibraryDb(context: Context, name: String = "biblioteca.db") : SQLiteOpenHelper(context, name, null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE books (
            id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT NOT NULL,
            title TEXT NOT NULL, author TEXT NOT NULL, genre TEXT NOT NULL,
            total INTEGER NOT NULL CHECK(total > 0), pages_read INTEGER NOT NULL CHECK(pages_read >= 0 AND pages_read <= total),
            state TEXT NOT NULL, rating INTEGER NOT NULL DEFAULT 0, finished_at INTEGER, cover_path TEXT)""")
        db.execSQL("CREATE INDEX books_owner ON books(uid)")
        db.execSQL("CREATE TABLE goals(uid TEXT NOT NULL, year INTEGER NOT NULL, target INTEGER NOT NULL CHECK(target > 0), PRIMARY KEY(uid, year))")
        db.execSQL("CREATE TABLE reading(uid TEXT NOT NULL, book_id INTEGER NOT NULL, year INTEGER NOT NULL, pages INTEGER NOT NULL, PRIMARY KEY(uid, book_id, year))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE books ADD COLUMN cover_path TEXT")
    }
    fun books(uid: String): List<Book> {
        require(uid.isNotBlank())
        val result = mutableListOf<Book>()
        readableDatabase.query("books", null, "uid = ?", arrayOf(uid), null, null, "id DESC").use { c ->
            while (c.moveToNext()) result += Book(c.getLong(c.getColumnIndexOrThrow("id")), c.getString(c.getColumnIndexOrThrow("title")),
                c.getString(c.getColumnIndexOrThrow("author")), c.getString(c.getColumnIndexOrThrow("genre")),
                c.getInt(c.getColumnIndexOrThrow("total")), c.getInt(c.getColumnIndexOrThrow("pages_read")),
                c.getString(c.getColumnIndexOrThrow("state")), c.getInt(c.getColumnIndexOrThrow("rating")),
                c.getColumnIndexOrThrow("finished_at").let { if (c.isNull(it)) null else c.getLong(it) },
                c.getString(c.getColumnIndexOrThrow("cover_path")))
        }
        return result
    }
    fun save(uid: String, book: Book, now: Long = System.currentTimeMillis()): Long {
        require(uid.isNotBlank())
        require(book.genre in GENRES)
        require(BookRules.validate(book.title, book.author, book.total, book.read, book.state, book.rating).isEmpty())
        val db = writableDatabase
        db.beginTransaction()
        try {
            val previous = if (book.id == 0L) null else {
                books(uid).firstOrNull { it.id == book.id } ?: error("No se encontró el libro para este usuario.")
            }
            val finished = if (book.state == "Terminado") previous?.finishedAt ?: now else null
            val values = ContentValues().apply {
                put("uid", uid); put("title", book.title.trim()); put("author", book.author.trim()); put("genre", book.genre)
                put("total", book.total); put("pages_read", book.read); put("state", book.state); put("rating", book.rating)
                put("cover_path", book.coverPath)
                if (finished == null) putNull("finished_at") else put("finished_at", finished)
            }
            val id = if (previous == null) db.insertOrThrow("books", null, values) else {
                check(db.update("books", values, "uid = ? AND id = ?", arrayOf(uid, book.id.toString())) == 1); book.id
            }
            // Positive progress is assigned to the current year. Corrections reduce newest recorded progress first.
            var delta = book.read - (previous?.read ?: 0)
            if (delta > 0) {
                val year = BookRules.year(now)
                db.execSQL("INSERT OR IGNORE INTO reading(uid,book_id,year,pages) VALUES(?,?,?,0)", arrayOf<Any>(uid, id, year))
                db.execSQL("UPDATE reading SET pages=pages+? WHERE uid=? AND book_id=? AND year=?", arrayOf<Any>(delta, uid, id, year))
            }
            if (delta < 0) {
                db.rawQuery("SELECT year,pages FROM reading WHERE uid=? AND book_id=? ORDER BY year DESC", arrayOf(uid, id.toString())).use { c ->
                    while (c.moveToNext() && delta < 0) {
                        val reduction = minOf(c.getInt(1), -delta)
                        db.execSQL("UPDATE reading SET pages=pages-? WHERE uid=? AND book_id=? AND year=?", arrayOf<Any>(reduction, uid, id, c.getInt(0)))
                        delta += reduction
                    }
                }
            }
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }
    fun goal(uid: String, year: Int): Int? = readableDatabase.rawQuery("SELECT target FROM goals WHERE uid=? AND year=?", arrayOf(uid, year.toString())).use { if (it.moveToFirst()) it.getInt(0) else null }
    fun setGoal(uid: String, year: Int, target: Int) {
        require(uid.isNotBlank() && target > 0)
        writableDatabase.insertWithOnConflict("goals", null, ContentValues().apply { put("uid", uid); put("year", year); put("target", target) }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun pages(uid: String, year: Int): Long = readableDatabase.rawQuery("SELECT COALESCE(SUM(pages),0) FROM reading WHERE uid=? AND year=?", arrayOf(uid, year.toString())).use { it.moveToFirst(); it.getLong(0) }
}

