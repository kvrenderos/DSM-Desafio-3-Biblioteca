package com.example.desafiobiblioteca

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.tasks.Tasks
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Real Firebase integration. Creates and removes one temporary test account; never emails it. */
@RunWith(AndroidJUnit4::class)
class ReadingFlowTest {
    private fun pickerFilter() = android.content.IntentFilter(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
        addDataType("*/*")
        addCategory(android.content.Intent.CATEGORY_OPENABLE)
    }
    private fun waitFor(text: String) {
        val limit = System.currentTimeMillis() + 15000
        while (true) {
            try { onView(withText(text)).check(matches(isDisplayed())); return }
            catch (error: Exception) { if (System.currentTimeMillis() >= limit) throw error }
            Thread.sleep(250)
        }
    }
    private fun field(name: String, value: String) {
        val matcher = withTagValue(`is`(name as Any))
        try { onView(matcher).perform(scrollTo()) } catch (_: androidx.test.espresso.PerformException) { }
        onView(matcher).perform(replaceText(value),androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
    }
    private fun clickText(text: String) {
        try { onView(withText(text)).perform(scrollTo()) } catch (_: androidx.test.espresso.PerformException) { }
        onView(withText(text)).perform(click())
    }
    private fun screenshot(name: String) {
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.waitForIdleSync()
        // Let the compositor present text updates and ScrollView movement before capture.
        Thread.sleep(650)
        val file = File(inst.targetContext.getExternalFilesDir(null),"evidencias/$name.png")
        file.parentFile?.mkdirs()
        inst.uiAutomation.takeScreenshot().also { bitmap -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
    }
    @Test fun fullReadingJourney() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val email = "lectora${System.currentTimeMillis().toString().takeLast(6)}@example.com"
        val password = "Lectura!2026"
        var createdUid: String? = null
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            screenshot("01-acceso")
            clickText("Soy nueva · Crear cuenta")
            field("Correo electrónico",email); field("Contraseña",password); field("Confirmar contraseña",password)
            scenario.onActivity { activity ->
                val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                listOf("Contraseña", "Confirmar contraseña").forEach { tag ->
                    val edit = content.findViewWithTag<android.widget.EditText>(tag)
                    assertTrue("Password must stay masked", edit.transformationMethod is android.text.method.PasswordTransformationMethod)
                }
            }
            screenshot("02-registro")
            clickText("Crear cuenta")
            val limit = System.currentTimeMillis()+45000
            while (auth.currentUser == null && System.currentTimeMillis()<limit) Thread.sleep(250)
            assertNotNull("Firebase signup failed. Enable Email/Password and check google-services.json.",auth.currentUser)
            createdUid = auth.currentUser!!.uid
            inst.waitForIdleSync()
            waitFor("Definir meta anual")
            clickText("Definir meta anual")
            val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            field("Libros para $year","0"); clickText("Guardar")
            onView(withText("La meta debe ser un entero mayor que cero.")).check(matches(isDisplayed()))
            field("Libros para $year","12"); clickText("Guardar")
            clickText("+ Agregar libro")
            field("Título","El principito"); field("Autor","Antoine de Saint-Exupéry"); field("Total de páginas","120")
            onView(withContentDescription("Estado")).perform(scrollTo(),click())
            onData(allOf(`is`(instanceOf(String::class.java)),`is`("Leyendo"))).perform(click())
            field("Páginas leídas","45")
            screenshot("03-formulario")
            clickText("Guardar libro")
            val db = LibraryDb(inst.targetContext)
            try {
                assertEquals(45,db.books(createdUid).single().read)
                // Additional fixtures exercise all four genres and a real rating tie.
                val now=System.currentTimeMillis()
                db.save(createdUid,Book(title="Hábitos atómicos",author="James Clear",genre="Desarrollo personal",total=320,read=320,state="Terminado",rating=5),now-10000)
                db.save(createdUid,Book(title="Una breve historia",author="Autora de ejemplo",genre="Historia",total=180,read=180,state="Terminado",rating=4),now-5000)
                db.save(createdUid,Book(title="Fahrenheit 451",author="Ray Bradbury",genre="Ciencia ficción",total=200,read=200,state="Terminado",rating=5),now)
                db.save(createdUid,Book(title="Orgullo y prejuicio",author="Jane Austen",genre="Novela",total=400,read=0,state="Por leer"),now)
            } finally { db.close() }
            onView(withText("Inicio")).perform(click())
            screenshot("04-inicio")
            onView(withContentDescription("Abrir menú")).perform(click())
            screenshot("05-menu")
            androidx.test.espresso.Espresso.pressBack()
            onView(withText("Biblioteca")).perform(click())
            screenshot("06-biblioteca")
            clickText("Editar · El principito")
            val fixture = coverFixture(inst.targetContext.cacheDir)
            val pickerResult = android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,
                android.content.Intent().setData(android.net.Uri.fromFile(fixture)))
            val picker = inst.addMonitor(pickerFilter(), pickerResult, true)
            try {
                clickText("Elegir o cambiar imagen")
                waitFor("Imagen elegida. Guarda el libro para conservarla.")
                assertEquals(1,picker.hits)
            } finally { inst.removeMonitor(picker); fixture.delete() }
            field("Páginas leídas","60")
            scenario.recreate()
            onView(withTagValue(`is`("Título" as Any))).check(matches(withText("El principito")))
            onView(withTagValue(`is`("Páginas leídas" as Any))).check(matches(withText("60")))
            onView(withText("Portada (opcional)")).perform(scrollTo())
            screenshot("10-portada-formulario")
            clickText("Guardar libro")
            onView(withText("Editar · El principito")).perform(scrollTo())
            screenshot("11-libro-con-portada")
            val coverDb = LibraryDb(inst.targetContext)
            val storedCover = try { coverDb.books(createdUid).first { it.title=="El principito" }.coverPath } finally { coverDb.close() }
            assertNotNull(storedCover)
            assertNotNull(CoverStore(inst.targetContext,createdUid).file(storedCover))
            clickText("Editar · El principito")
            val replacement = coverFixture(inst.targetContext.cacheDir)
            val replacePicker = inst.addMonitor(pickerFilter(),android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,
                android.content.Intent().setData(android.net.Uri.fromFile(replacement))),true)
            try {
                clickText("Elegir o cambiar imagen")
                waitFor("Imagen elegida. Guarda el libro para conservarla.")
                assertEquals(1,replacePicker.hits)
            } finally { inst.removeMonitor(replacePicker); replacement.delete() }
            clickText("Guardar libro")
            assertNull(CoverStore(inst.targetContext,createdUid).file(storedCover))
            val replacementDb = LibraryDb(inst.targetContext)
            val newCover = try { replacementDb.books(createdUid).first { it.title=="El principito" }.coverPath } finally { replacementDb.close() }
            assertNotEquals(storedCover,newCover)
            assertNotNull(CoverStore(inst.targetContext,createdUid).file(newCover))
            clickText("Editar · El principito")
            val cancelled = inst.addMonitor(pickerFilter(),
                android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED,null),true)
            try {
                clickText("Elegir o cambiar imagen")
                assertEquals(1,cancelled.hits)
                onView(withText("Imagen elegida. Guarda el libro para conservarla.")).check(matches(isDisplayed()))
            } finally { inst.removeMonitor(cancelled) }
            clickText("Quitar imagen")
            clickText("Guardar libro")
            assertNull(CoverStore(inst.targetContext,createdUid).file(newCover))
            scenario.recreate()
            val reopened = LibraryDb(inst.targetContext)
            try { assertEquals(60,reopened.books(createdUid).first { it.title=="El principito" }.read) } finally { reopened.close() }
            onView(withText("Estadísticas")).perform(click())
            screenshot("07-estadisticas")
            onView(withText("Editar · Hábitos atómicos")).perform(scrollTo())
            screenshot("08-mejor-libro")
            onView(withContentDescription("Abrir menú")).perform(click())
            clickText("Cerrar sesión")
            assertNull(auth.currentUser)
            field("Correo electrónico",email); field("Contraseña",password)
            clickText("Iniciar sesión")
            val signInLimit=System.currentTimeMillis()+45000
            while(auth.currentUser==null && System.currentTimeMillis()<signInLimit) Thread.sleep(250)
            assertEquals(createdUid,auth.currentUser?.uid)
            waitFor("3 de 12 libros")
            scenario.recreate()
            assertEquals(createdUid,auth.currentUser?.uid)
            onView(withText("3 de 12 libros")).check(matches(isDisplayed()))
            screenshot("09-sesion-restaurada")
        } finally {
            scenario.close()
            if (createdUid!=null) {
                val db=LibraryDb(inst.targetContext)
                db.books(createdUid).forEach { CoverStore(inst.targetContext,createdUid).remove(it.coverPath) }
                listOf("books","goals","reading").forEach { db.writableDatabase.delete(it,"uid=?",arrayOf(createdUid)) }; db.close()
                auth.currentUser?.let { Tasks.await(it.delete(),30,TimeUnit.SECONDS) }
            }
            auth.signOut()
        }
    }
}


