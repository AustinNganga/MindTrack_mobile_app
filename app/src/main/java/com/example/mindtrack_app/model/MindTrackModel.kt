package com.example.mindtrack_app.model

import org.json.JSONObject

/**
 * On-device MindTrack depression-risk model: a random forest exported from
 * MindTrack_Project/notebooks/Pipeline.ipynb as plain JSON (assets/mindtrack_model.json).
 *
 * Each tree is stored as parallel arrays. Prediction walks every tree (left when x <= threshold)
 * and averages the leaf probabilities, exactly like scikit-learn's RandomForestClassifier.
 * Output: estimated probability that the Beck Depression Inventory total is >= 11.
 *
 * This is a screening estimate from questionnaire answers, not a diagnosis.
 */
class MindTrackModel private constructor(
    val features: List<String>,
    private val trees: List<Tree>,
    val elevatedThreshold: Double,
    val version: String,
    val disclaimer: String,
) {
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
    }

    data class Prediction(val probability: Double, val elevated: Boolean)

    /** [inputs] must contain every name in [features]. */
    fun predict(inputs: Map<String, Double>): Prediction {
        val missing = features.filter { it !in inputs }
        require(missing.isEmpty()) { "Missing model inputs: $missing" }
        val x = DoubleArray(features.size) { inputs.getValue(features[it]) }
        var total = 0.0
        for (tree in trees) total += tree.predict(x)
        val p = total / trees.size
        return Prediction(p, p >= elevatedThreshold)
    }

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
            )
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
