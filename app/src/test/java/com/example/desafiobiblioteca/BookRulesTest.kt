package com.example.desafiobiblioteca

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class BookRulesTest {
    private fun errors(total: Int? = 100, read: Int? = 20, state: String = "Leyendo", rating: Int = 0) =
        BookRules.validate("El principito", "Antoine de Saint-Exupéry", total, read, state, rating)
    @Test fun validatesBoundariesAndRequiredFields() {
        assertTrue(errors().isEmpty())
        assertTrue(errors(total = null).containsKey("total"))
        assertTrue(errors(total = 0).containsKey("total"))
        assertTrue(errors(read = -1).containsKey("read"))
        assertTrue(errors(read = 101).containsKey("read"))
        assertTrue(errors(read = null).containsKey("read"))
        assertTrue(BookRules.validate("A", "B", 100, 0, "Por leer", 0).keys.containsAll(listOf("title", "author")))
        assertTrue(BookRules.validate("a".repeat(60), "b".repeat(60), Int.MAX_VALUE, 0, "Por leer", 0).isEmpty())
        assertTrue(BookRules.validate("a".repeat(61), "bb", 1, 0, "Por leer", 0).containsKey("title"))
    }
    @Test fun ratingsAndStatesStayConsistent() {
        assertTrue(errors(read = 100, state = "Terminado", rating = 5).isEmpty())
        assertTrue(errors(read = 100, state = "Terminado", rating = 0).containsKey("rating"))
        assertTrue(errors(rating = 3).containsKey("rating"))
        assertTrue(errors(state = "Por leer").containsKey("read"))
        assertTrue(errors(state = "Terminado", rating = 5).containsKey("read"))
        assertEquals(100, Book(title="aa", author="bb", genre="Novela", total=Int.MAX_VALUE, read=Int.MAX_VALUE, state="Terminado", rating=5).percent)
    }
    @Test fun filtersYearAndBreaksRatingTiesByEarliestFinish() {
        fun date(year: Int) = Calendar.getInstance().apply { set(year, 2, 10, 12, 0, 0) }.timeInMillis
        fun book(id: Long, rating: Int, time: Long) = Book(id,"Libro $id","Autor","Novela",100,100,"Terminado",rating,time)
        val list = listOf(book(1,5,date(2025)),book(2,5,date(2026)+1000),book(3,5,date(2026)),book(4,4,date(2026)-1000))
        val current = BookRules.finishedThisYear(list,2026)
        assertEquals(3,current.size)
        assertEquals(3L,BookRules.best(current)?.id)
        assertNull(BookRules.best(emptyList()))
    }
}
