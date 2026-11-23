package com.example.mindtrack_app.model

import org.json.JSONObject

/** Thrown when the model is given missing, non-numeric or out-of-range inputs. */
class InvalidInputException(val problems: List<String>) :
    IllegalArgumentException("Invalid model input: " + problems.joinToString("; "))

/**
 * On-device MindTrack depression-risk model: a random forest exported from
 * MindTrack_Project/notebooks/Pipeline.ipynb as plain JSON (assets/mindtrack_model.json).
 *
 * Each tree is stored as parallel arrays. Prediction walks every tree (left when x <= threshold)
 * and averages the leaf probabilities, exactly like scikit-learn's RandomForestClassifier.
 * Output: estimated probability that the Beck Depression Inventory total is >= 11.
 *
 * Explainable AI: [explain] splits each prediction into "starting point + one contribution per input"
 * using tree-path contributions (the Saabas method, the idea behind tree SHAP). Every time a tree
 * splits on an input, the change in the node's value is credited to that input. Averaged over the
 * 300 trees, starting point + all contributions = the prediction, exactly.
 *
 * This is a screening estimate from questionnaire answers, not a diagnosis.
 */
class MindTrackModel private constructor(
    val features: List<String>,
    private val trees: List<Tree>,
    val elevatedThreshold: Double,
    val version: String,
    val disclaimer: String,
    private val rules: Map<String, Rule>,
) {
    /** What values an input may take (from field_info in the model file). */
    sealed interface Rule {
        data class Range(val min: Double, val max: Double) : Rule
        data class Codes(val allowed: Set<Int>) : Rule
    }

    private class Tree(
        val feature: IntArray,
        val threshold: DoubleArray,
        val left: IntArray,
        val right: IntArray,
        val value: DoubleArray,
    ) {
        fun predict(x: DoubleArray): Double {
            var node = 0
            while (left[node] != -1) {
                node = if (x[feature[node]] <= threshold[node]) left[node] else right[node]
            }
            return value[node]
        }

        /** Same walk as [predict], but credits each step's change in value to the input that was split on. */
        fun predictAndExplain(x: DoubleArray, contributions: DoubleArray): Double {
            var node = 0
            while (left[node] != -1) {
                val next = if (x[feature[node]] <= threshold[node]) left[node] else right[node]
                contributions[feature[node]] += value[next] - value[node]
                node = next
            }
            return value[node]
        }

        val rootValue: Double get() = value[0]
    }

    data class Prediction(val probability: Double, val elevated: Boolean)

    /**
     * [baseline] is the average starting value of the trees; [contributions] says how much each input
     * pushed the score up (+) or down (−). baseline + sum(contributions) == probability.
     */
    data class Explanation(
        val probability: Double,
        val baseline: Double,
        val contributions: Map<String, Double>,
    )

    /** Problems with [inputs] in plain language; empty if everything is valid. */
    fun validate(inputs: Map<String, Double>): List<String> {
        val problems = mutableListOf<String>()
        for (f in features) {
            val v = inputs[f]
            when {
                v == null -> problems += "$f is missing"
                v.isNaN() || v.isInfinite() -> problems += "$f is not a number"
                else -> when (val rule = rules[f]) {
                    is Rule.Range -> if (v < rule.min || v > rule.max) {
                        problems += "$f must be between ${fmt(rule.min)} and ${fmt(rule.max)} (got ${fmt(v)})"
                    }
                    is Rule.Codes -> if (v != Math.floor(v) || v.toInt() !in rule.allowed) {
                        problems += "$f must be one of ${rule.allowed.sorted().joinToString()} (got ${fmt(v)})"
                    }
                    null -> Unit
                }
            }
        }
        return problems
    }

    /** Checks [inputs] first and throws [InvalidInputException] rather than guessing with bad data. */
    fun predict(inputs: Map<String, Double>): Prediction {
        val x = vectorOf(inputs)
        var total = 0.0
        for (tree in trees) total += tree.predict(x)
        val p = total / trees.size
        return Prediction(p, p >= elevatedThreshold)
    }

    /** The prediction broken down into one contribution per input (see class comment). */
    fun explain(inputs: Map<String, Double>): Explanation {
        val x = vectorOf(inputs)
        val sums = DoubleArray(features.size)
        var total = 0.0
        var base = 0.0
        for (tree in trees) {
            total += tree.predictAndExplain(x, sums)
            base += tree.rootValue
        }
        val n = trees.size
        return Explanation(
            probability = total / n,
            baseline = base / n,
            contributions = features.indices.associate { features[it] to sums[it] / n },
        )
    }

    private fun vectorOf(inputs: Map<String, Double>): DoubleArray {
        val problems = validate(inputs)
        if (problems.isNotEmpty()) throw InvalidInputException(problems)
        return DoubleArray(features.size) { inputs.getValue(features[it]) }
    }

    private fun fmt(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

    companion object {
        fun fromJson(json: String): MindTrackModel {
            val root = JSONObject(json)
            val featureArray = root.getJSONArray("features")
            val features = List(featureArray.length()) { featureArray.getString(it) }
            val treeArray = root.getJSONArray("trees")
            val trees = List(treeArray.length()) { i ->
                val t = treeArray.getJSONObject(i)
                Tree(
                    feature = t.intArray("feature"),
                    threshold = t.doubleArray("threshold"),
                    left = t.intArray("left"),
                    right = t.intArray("right"),
                    value = t.doubleArray("value"),
                )
            }
            return MindTrackModel(
                features = features,
                trees = trees,
                elevatedThreshold = root.getJSONObject("thresholds").getDouble("elevated"),
                version = root.optString("version", "unknown"),
                disclaimer = root.optString("disclaimer", "Screening estimate, not a diagnosis."),
                rules = parseRules(root.optJSONObject("field_info")),
            )
        }

        /** field_info gives either min/max, an "options" object (codes as keys) or "allowed_codes". */
        private fun parseRules(info: JSONObject?): Map<String, Rule> {
            if (info == null) return emptyMap()
            val rules = mutableMapOf<String, Rule>()
            for (name in info.keys()) {
                val f = info.getJSONObject(name)
                when {
                    f.has("min") && f.has("max") -> rules[name] = Rule.Range(f.getDouble("min"), f.getDouble("max"))
                    f.has("options") -> rules[name] = Rule.Codes(f.getJSONObject("options").keys().asSequence().map { it.toInt() }.toSet())
                    f.has("allowed_codes") -> {
                        val a = f.getJSONArray("allowed_codes")
                        rules[name] = Rule.Codes(List(a.length()) { a.getInt(it) }.toSet())
                    }
                }
            }
            return rules
        }

        private fun JSONObject.intArray(key: String): IntArray {
            val a = getJSONArray(key)
            return IntArray(a.length()) { a.getInt(it) }
        }

        private fun JSONObject.doubleArray(key: String): DoubleArray {
            val a = getJSONArray(key)
            return DoubleArray(a.length()) { a.getDouble(it) }
        }
    }
}
