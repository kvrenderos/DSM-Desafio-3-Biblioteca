package com.example.desafiobiblioteca

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Native views keep this project small and easy to follow in Android Studio. */
class MainActivity : AppCompatActivity() {
    private val ink = Color.parseColor("#2B2F35")
    private val muted = Color.parseColor("#565C63")
    private val rose = Color.parseColor("#C7A1AD")
    private val paper = Color.parseColor("#F7EEF1")
    private val cream = Color.parseColor("#F3E2C6")
    private val accent = Color.parseColor("#79505E")
    private lateinit var auth: FirebaseAuth
    private lateinit var db: LibraryDb
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private var screen = "Inicio"
    private var busy = false
    private val year get() = Calendar.getInstance().get(Calendar.YEAR)
    private val uid get() = requireNotNull(auth.currentUser).uid
    private data class Field(val layout: TextInputLayout, val edit: TextInputEditText) {
        val value get() = edit.text.toString().trim()
        fun error(message: String?) { layout.error = message }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = LibraryDb(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goBack() }
        })
        screen = savedInstanceState?.getString("screen") ?: "Inicio"
        if (auth.currentUser == null) showAuth() else navigate(screen)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", if (screen == "Libro") "Biblioteca" else screen)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { db.close(); super.onDestroy() }
    private fun goBack() {
        if (auth.currentUser != null && screen != "Inicio") {
            if (screen == "Libro") AlertDialog.Builder(this).setTitle("¿Salir sin guardar?").setMessage("Los cambios del formulario se descartarán.")
                .setNegativeButton("Continuar", null).setPositiveButton("Salir") { _, _ -> navigate("Biblioteca") }.show()
            else navigate("Inicio")
        } else finish()
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun background(color: Int, radius: Int = 20) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun column(parent: LinearLayout, color: Int? = null, padding: Int = 0): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
        if (color != null) background = background(color)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
    }
    private fun label(parent: LinearLayout, text: String, size: Int = 16, color: Int = ink, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(), 1f)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
    }
    private fun button(parent: LinearLayout, text: String, primary: Boolean = true, action: () -> Unit): MaterialButton = MaterialButton(this).apply {
        this.text = text; isAllCaps = false; textSize = 15f; cornerRadius = dp(14)
        setTextColor(if (primary) Color.WHITE else ink)
        backgroundTintList = android.content.res.ColorStateList.valueOf(if (primary) accent else cream)
        parent.addView(this, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(6) })
        setOnClickListener { action() }
    }
    private fun field(parent: LinearLayout, hint: String, value: String = "", numeric: Boolean = false, secret: Boolean = false): Field {
        val layout = TextInputLayout(this).apply {
            this.hint = hint; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
            if (secret) endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val edit = TextInputEditText(layout.context).apply {
            setText(value); setTextColor(ink); textSize = 16f
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true); tag = hint
        }
        layout.addView(edit)
        if (secret) edit.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
        parent.addView(layout, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        return Field(layout, edit)
    }
    private fun base(title: String, navigation: Boolean = true) {
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(currentFocus?.windowToken, 0)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(paper)
            isFocusableInTouchMode = true; requestFocus()
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        setContentView(root)
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(6), dp(20), dp(6)) }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(64)))
        if (navigation) {
            val menu = TextView(this).apply { text = "☰"; textSize = 27f; setTextColor(ink); gravity = Gravity.CENTER; contentDescription = "Abrir menú"; setOnClickListener { drawer() } }
            toolbar.addView(menu, LinearLayout.LayoutParams(dp(48), -1))
        }
        label(toolbar, title, 21, ink, true)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(12), dp(24), dp(24)) }
        scroll.addView(body)
        if (navigation && screen != "Libro") {
            val tabs = LinearLayout(this).apply { setBackgroundColor(Color.WHITE); setPadding(dp(8), dp(5), dp(8), dp(5)) }
            root.addView(tabs)
            listOf("Inicio", "Biblioteca", "Estadísticas").forEach { name ->
                val item = TextView(this).apply {
                    text = name; textSize = 13f; gravity = Gravity.CENTER; setTextColor(if (screen == name) accent else muted)
                    if (screen == name) { typeface = Typeface.DEFAULT_BOLD; background = background(paper, 12) }
                    setOnClickListener { navigate(name) }
                }
                tabs.addView(item, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
        }
    }
    private fun drawer() {
        val dialog = Dialog(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(48), dp(24), dp(24)); setBackgroundColor(paper) }
        val header = column(content, rose, 20)
        label(header, "B /", 40, ink, true)
        label(header, "Entre páginas", 23, ink, true)
        label(header, "Mi diario de lectura", 14)
        label(content, auth.currentUser?.email ?: "", 13, muted)
        listOf("Inicio", "Biblioteca", "Agregar libro", "Meta anual", "Estadísticas").forEach { name ->
            button(content, name, false) { dialog.dismiss(); when (name) { "Agregar libro" -> editor(); "Meta anual" -> goalDialog(); else -> navigate(name) } }
        }
        label(content, "Cada libro cuenta.", 14, muted)
        button(content, "Cerrar sesión") { dialog.dismiss(); auth.signOut(); showAuth() }
        val scroll = ScrollView(this).apply { addView(content) }
        dialog.setContentView(scroll)
        dialog.window?.apply { setBackgroundDrawableResource(android.R.color.transparent); setGravity(Gravity.START) }
        dialog.show(); dialog.window?.setLayout(dp(320), -1)
    }
    private fun showAuth(register: Boolean = false) {
        screen = "Acceso"; busy = false
        base("Entre páginas", false)
        val hero = column(body, rose, 24)
        label(hero, "B /", 48, ink, true)
        label(hero, "Tu próxima historia\nempieza aquí.", 29, ink, true)
        label(hero, "Un espacio para tus libros, tus avances y tus nuevas metas.", 15)
        label(body, if (register) "Crea tu cuenta" else "Bienvenida a tu biblioteca", 23, ink, true)
        label(body, "Ingresa con tu correo y contraseña.", 14, muted)
        val email = field(body, "Correo electrónico")
        email.edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        val password = field(body, "Contraseña", secret = true)
        val confirm = if (register) field(body, "Confirmar contraseña", secret = true) else null
        val message = label(body, "", 14, accent)
        lateinit var submit: MaterialButton
        lateinit var switch: MaterialButton
        submit = button(body, if (register) "Crear cuenta" else "Iniciar sesión") {
            if (!busy) {
                email.error(null); password.error(null); confirm?.error(null); message.text = ""
                var valid = true
                if (!Patterns.EMAIL_ADDRESS.matcher(email.value).matches()) { email.error("Escribe un correo electrónico válido."); valid = false }
                val rawPassword = password.edit.text.toString()
                if (rawPassword.length < 6) { password.error("La contraseña debe tener al menos 6 caracteres."); valid = false }
                if (confirm != null && confirm.edit.text.toString() != rawPassword) { confirm.error("Las contraseñas no coinciden."); valid = false }
                if (valid) {
                    busy = true; submit.isEnabled = false; switch.isEnabled = false; message.text = "Conectando…"
                    val task = if (register) auth.createUserWithEmailAndPassword(email.value, rawPassword) else auth.signInWithEmailAndPassword(email.value, rawPassword)
                    task.addOnCompleteListener(this) { result ->
                        busy = false
                        if (result.isSuccessful) navigate("Inicio") else {
                            submit.isEnabled = true; switch.isEnabled = true
                            message.text = when (val error = result.exception) {
                                is FirebaseNetworkException -> "No hay conexión. Revisa tu internet e inténtalo otra vez."
                                is FirebaseAuthUserCollisionException -> "Este correo ya está registrado. Inicia sesión."
                                is FirebaseAuthWeakPasswordException -> "Firebase rechazó la contraseña. Usa una más segura."
                                is FirebaseAuthException -> if (error.errorCode == "ERROR_OPERATION_NOT_ALLOWED") "Activa Correo/contraseña en Firebase Authentication." else "No se pudo acceder. Revisa tus credenciales o inténtalo después."
                                else -> "No se pudo conectar con Firebase. Inténtalo de nuevo."
                            }
                        }
                    }
                }
            }
        }
        switch = button(body, if (register) "Ya tengo cuenta · Iniciar sesión" else "Soy nueva · Crear cuenta", false) { if (!busy) showAuth(!register) }
    }
    private fun navigate(destination: String) {
        if (auth.currentUser == null) { showAuth(); return }
        screen = destination
        when (destination) { "Biblioteca" -> library(); "Estadísticas" -> statistics(); else -> { screen = "Inicio"; home() } }
    }
    private fun home() {
        base("Entre páginas")
        label(body, "TU DIARIO DE LECTURA", 11, accent, true)
        label(body, "Hola, ${auth.currentUser?.email?.substringBefore('@')}.", 28, ink, true)
        label(body, "Un capítulo más, un poco más lejos.", 15, muted)
        val books = db.books(uid)
        val completed = BookRules.finishedThisYear(books, year).size
        val goal = db.goal(uid, year)
        val card = column(body, rose, 22)
        label(card, "MI META · $year", 12, ink, true)
        label(card, if (goal == null) "Tu año, tus historias" else "$completed de $goal libros", 28, ink, true)
        label(card, if (goal == null) "$completed terminados. Define tu meta para empezar." else if (completed >= goal) "¡Meta cumplida! Sigue descubriendo historias." else "Te faltan ${goal - completed} libros para cumplirla.", 15)
        if (goal != null) progress(card, (completed.toLong() * 100 / goal).coerceAtMost(100).toInt())
        button(card, if (goal == null) "Definir meta anual" else "Modificar meta anual", false) { goalDialog() }
        button(body, "+ Agregar libro") { editor() }
        label(body, "En tu mesa de lectura", 22, ink, true)
        val reading = books.filter { it.state == "Leyendo" }
        if (reading.isEmpty()) empty("Aún no tienes lecturas en curso.", "Agrega un libro y comienza a registrar tu avance.")
        else reading.forEach { bookCard(it) }
        button(body, "Ver biblioteca completa", false) { navigate("Biblioteca") }
        button(body, "Ver estadísticas", false) { navigate("Estadísticas") }
    }
    private fun goalDialog() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0) }
        val input = field(container, "Libros para $year", db.goal(uid, year)?.toString() ?: "", numeric = true)
        val dialog = AlertDialog.Builder(this).setTitle("Mi meta anual").setView(container).setNegativeButton("Cancelar", null).setPositiveButton("Guardar", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val target = input.value.toIntOrNull()
                if (target == null || target <= 0) input.error("La meta debe ser un entero mayor que cero.")
                else try { db.setGoal(uid, year, target); dialog.dismiss(); navigate("Inicio") } catch (_: Exception) { input.error("No se pudo guardar la meta. Inténtalo otra vez.") }
            }
        }
        dialog.show()
    }
    private fun progress(parent: LinearLayout, percent: Int) {
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = percent; progressTintList = android.content.res.ColorStateList.valueOf(accent)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E4D5DB"))
            contentDescription = "Avance $percent por ciento"
        }
        parent.addView(bar, LinearLayout.LayoutParams(-1, dp(8)).apply { bottomMargin = dp(12) })
    }
    private fun empty(title: String, description: String) {
        val card = column(body, Color.WHITE, 22)
        label(card, "B /", 30, accent, true); label(card, title, 18, ink, true); label(card, description, 14, muted)
    }
    private fun bookCard(book: Book) {
        val card = column(body, Color.WHITE, 18)
        label(card, "${book.genre.uppercase(Locale.getDefault())} · ${book.state}", 11, accent, true)
        label(card, book.title, 21, ink, true)
        label(card, book.author, 14, muted)
        label(card, "${book.read} / ${book.total} páginas · ${book.percent}%", 14)
        progress(card, book.percent)
        if (book.state == "Terminado") label(card, "${"★".repeat(book.rating)}${"☆".repeat(5-book.rating)}  ·  ${date(book.finishedAt)}", 15, accent)
        button(card, "Editar · ${book.title}", false) { editor(book) }
    }
    private fun library() {
        base("Mi biblioteca")
        label(body, "Historias que te acompañan", 26, ink, true)
        val books = db.books(uid)
        label(body, "${books.size} libros en tu colección", 14, muted)
        button(body, "+ Agregar libro") { editor() }
        if (books.isEmpty()) empty("Tu biblioteca está por comenzar.", "Agrega tu primer libro: por leer, en curso o terminado.") else books.forEach { bookCard(it) }
    }
    private fun selector(parent: LinearLayout, title: String, options: List<String>, selected: String): Spinner {
        label(parent, title, 14, muted, true)
        return Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, options)
            setSelection(options.indexOf(selected).coerceAtLeast(0)); contentDescription = title
            parent.addView(this, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(16) })
        }
    }
    private fun editor(book: Book? = null) {
        screen = "Libro"; base(if (book == null) "Nuevo libro" else "Editar libro")
        label(body, "Una historia para tu estante", 26, ink, true)
        label(body, "Completa los datos y guarda tu avance.", 14, muted)
        val title = field(body, "Título", book?.title ?: "")
        val author = field(body, "Autor", book?.author ?: "")
        val genre = selector(body, "Género", GENRES, book?.genre ?: GENRES[0])
        val total = field(body, "Total de páginas", book?.total?.toString() ?: "", numeric = true)
        val read = field(body, "Páginas leídas", book?.read?.toString() ?: "0", numeric = true)
        val state = selector(body, "Estado", STATES, book?.state ?: STATES[0])
        val ratingLabel = label(body, "Calificación · solo libros terminados", 14, muted, true)
        val rating = RatingBar(this).apply { numStars = 5; stepSize = 1f; this.rating = book?.rating?.toFloat() ?: 0f; contentDescription = "Calificación de 1 a 5 estrellas" }
        body.addView(rating, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(10) })
        val note = label(body, "", 13, accent)
        var firstSelection = true
        state.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val finished = STATES[position] == "Terminado"
                rating.isEnabled = finished; rating.alpha = if (finished) 1f else 0.35f
                ratingLabel.text = if (finished) "Calificación · obligatoria (1 a 5)" else "Calificación · solo libros terminados"
                if (!finished) rating.rating = 0f
                if (!firstSelection) {
                    if (finished) read.edit.setText(total.value)
                    if (STATES[position] == "Por leer") read.edit.setText("0")
                }
                firstSelection = false
                note.text = if (finished) "Se guardará la fecha al terminar. Las páginas leídas deben coincidir con el total." else "Actualiza tus páginas al avanzar en la lectura."
            }
        }
        button(body, "Guardar libro") {
            listOf(title, author, total, read).forEach { it.error(null) }
            val errors = BookRules.validate(title.value, author.value, total.value.toIntOrNull(), read.value.toIntOrNull(), state.selectedItem.toString(), rating.rating.toInt())
            val fields = mapOf("title" to title, "author" to author, "total" to total, "read" to read)
            errors.forEach { (key, error) -> fields[key]?.error(error) }
            if (errors.isNotEmpty()) { note.text = errors.values.joinToString("\n"); fields[errors.keys.first()]?.edit?.requestFocus() }
            else try {
                db.save(uid, Book(book?.id ?: 0, title.value, author.value, genre.selectedItem.toString(), total.value.toInt(), read.value.toInt(), state.selectedItem.toString(), rating.rating.toInt()))
                navigate("Biblioteca")
            } catch (error: Exception) { android.util.Log.e("Library", "Error al guardar el libro", error); note.text = "No se pudo guardar el libro. Revisa los datos e inténtalo otra vez." }
        }
        button(body, "Cancelar", false) { goBack() }
    }
    private fun statistics() {
        base("Estadísticas")
        label(body, "Tu año entre páginas", 27, ink, true)
        label(body, "LECTURAS DE $year", 12, accent, true)
        val completed = BookRules.finishedThisYear(db.books(uid), year)
        val overview = column(body, cream, 22)
        label(overview, "${completed.size} libros terminados", 26, ink, true)
        label(overview, "${db.pages(uid, year)} páginas leídas en el año", 17)
        label(overview, "Incluye avances de libros en curso. Los avances se registran en el año en que los guardas.", 12, muted)
        label(body, "Por género", 21, ink, true)
        val counts = GENRES.associateWith { genre -> completed.count { it.genre == genre } }
        val genres = column(body, Color.WHITE, 18)
        counts.forEach { (genre, count) -> label(genres, "$genre  ·  $count", 16); progress(genres, if (completed.isEmpty()) 0 else count * 100 / completed.size) }
        val highest = counts.values.maxOrNull() ?: 0
        val leaders = if (highest == 0) "Aún no hay libros terminados." else counts.filterValues { it == highest }.keys.joinToString(" · ")
        label(body, if (counts.values.count { it == highest } > 1 && highest > 0) "Géneros más leídos · empate" else "Género más leído", 18, ink, true)
        label(body, leaders, 16, muted)
        label(body, "Tu mejor lectura", 21, ink, true)
        val best = BookRules.best(completed)
        if (best == null) empty("La próxima favorita te espera.", "Termina y califica un libro para verlo aquí.") else {
            bookCard(best); label(body, "Si hay empate en estrellas, aparece el que terminaste primero.", 12, muted)
        }
    }
    private fun date(time: Long?): String = if (time == null) "" else SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("es-SV")).format(time)
}


