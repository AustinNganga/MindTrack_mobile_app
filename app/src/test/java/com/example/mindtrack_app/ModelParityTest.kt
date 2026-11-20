package com.example.mindtrack_app

import com.example.mindtrack_app.data.QuestionBank
import com.example.mindtrack_app.model.MindTrackModel
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Proves the Kotlin model gives the same probabilities as the Python notebook.
 * test_vectors.json is produced by MindTrack_Project/notebooks/Pipeline.ipynb (section 12).
 * Run: right-click this file in Android Studio > Run 'ModelParityTest'.
 */
class ModelParityTest {
    private val model = MindTrackModel.fromJson(File("src/main/assets/mindtrack_model.json").readText())

    @Test
    fun kotlinMatchesPython() {
        val text = javaClass.classLoader!!.getResource("test_vectors.json")!!.readText()
        val vectors = JSONArray(text)
        assertTrue(vectors.length() > 0)
        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val inputs = v.getJSONObject("inputs")
            val map = inputs.keys().asSequence().associateWith { inputs.getDouble(it) }
            val expected = v.getDouble("expected_probability")
            assertEquals("vector $i", expected, model.predict(map).probability, 1e-6)
        }
    }

    @Test
    fun everyModelInputHasAQuestion() {
        assertEquals(emptyList<String>(), QuestionBank.uncovered(model.features))
    }
}
