package com.example.desafiobiblioteca

import java.util.Calendar

val GENRES = listOf("Novela", "Ciencia ficción", "Historia", "Desarrollo personal")
val STATES = listOf("Por leer", "Leyendo", "Terminado")

data class Book(
    val id: Long = 0,
    val title: String,
    val author: String,
    val genre: String,
    val total: Int,
    val read: Int,
    val state: String,
    val rating: Int = 0,
    val finishedAt: Long? = null,
    val coverPath: String? = null
) {
    val percent: Int get() = if (total > 0) (read.toLong() * 100 / total).toInt() else 0
}

object BookRules {
    fun validate(title: String, author: String, total: Int?, read: Int?, state: String, rating: Int): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        if (title.trim().length !in 2..60) errors["title"] = "El título debe tener entre 2 y 60 caracteres."
        if (author.trim().length !in 2..60) errors["author"] = "El autor debe tener entre 2 y 60 caracteres."
        if (total == null || total <= 0) errors["total"] = "El total de páginas debe ser un entero mayor que cero."
        if (read == null || read < 0 || (total != null && read > total)) errors["read"] = "Las páginas leídas deben estar entre 0 y el total."
        if (state !in STATES) errors["state"] = "Selecciona un estado válido."
        if (state == "Terminado" && rating !in 1..5) errors["rating"] = "Un libro terminado necesita de 1 a 5 estrellas."
        if (state != "Terminado" && rating != 0) errors["rating"] = "Solo puedes calificar un libro terminado."
        if (state == "Terminado" && total != null && read != total) errors["read"] = "Un libro terminado debe tener todas sus páginas leídas."
        if (state == "Por leer" && read != 0) errors["read"] = "Un libro por leer debe tener 0 páginas leídas."
        return errors
    }
    fun year(timestamp: Long): Int = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)
    fun finishedThisYear(books: List<Book>, year: Int) = books.filter { it.state == "Terminado" && it.finishedAt?.let(::year) == year }
    fun best(books: List<Book>): Book? = books.sortedWith(compareByDescending<Book> { it.rating }.thenBy { it.finishedAt ?: Long.MAX_VALUE }.thenBy { it.id }).firstOrNull()
}
