package com.example.mindtrack_app

import com.example.mindtrack_app.data.QuestionBank
import com.example.mindtrack_app.model.InvalidInputException
import com.example.mindtrack_app.model.MindTrackModel
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Tests for the explainable-AI breakdown and the model's input validation. */
class ModelExplainTest {
    private val model = MindTrackModel.fromJson(File("src/main/assets/mindtrack_model.json").readText())

    private fun vectors(): List<Map<String, Double>> {
        val arr = JSONArray(javaClass.classLoader!!.getResource("test_vectors.json")!!.readText())
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i).getJSONObject("inputs")
            o.keys().asSequence().associateWith { o.getDouble(it) }
        }
    }

    @Test
    fun explanationAddsUpToThePrediction() {
        for ((i, x) in vectors().withIndex()) {
            val e = model.explain(x)
            assertEquals("vector $i", model.predict(x).probability, e.probability, 1e-9)
            assertEquals("vector $i", e.probability, e.baseline + e.contributions.values.sum(), 1e-9)
        }
    }

    @Test
    fun everyInputHasAReadableLabel() {
        val x = vectors().first()
        for (f in model.features) {
            assertNotEquals(f, QuestionBank.questionFor(f))
            assertTrue(QuestionBank.answerText(f, x.getValue(f)).isNotBlank())
        }
    }

    @Test
    fun rejectsAnAgeOutsideTheStudyRange() {
        val x = vectors().first() + ("Age" to 200.0)
        val e = assertThrows(InvalidInputException::class.java) { model.predict(x) }
        assertTrue(e.problems.single().startsWith("Age"))
    }

    @Test
    fun rejectsAnAnswerCodeThatDoesNotExist() {
        val x = vectors().first() + ("STAI_item_21" to 7.0)
        assertThrows(InvalidInputException::class.java) { model.predict(x) }
    }

    @Test
    fun rejectsMissingAndNonNumericInputs() {
        val missing = vectors().first() - "TMD_Total"
        assertThrows(InvalidInputException::class.java) { model.predict(missing) }
        val notANumber = vectors().first() + ("Semester" to Double.NaN)
        assertThrows(InvalidInputException::class.java) { model.predict(notANumber) }
    }

    @Test
    fun allValidTestVectorsPassValidation() {
        vectors().forEach { assertEquals(emptyList<String>(), model.validate(it)) }
    }
}
