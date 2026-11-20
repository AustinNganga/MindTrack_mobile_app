package com.example.mindtrack_app.data

/** The three parts of a check-in. */
enum class Section(val number: Int, val title: String, val blurb: String, val minutes: String) {
    ABOUT(1, "About you", "A few quick questions about your studies and home life.", "under a minute"),
    PHONE(2, "Your phone habits", "22 short statements about how you use your phone. Go with your first instinct.", "about 2 minutes"),
    FEELINGS(3, "How you've been feeling", "Some questions about right now, and some about how you usually feel. There are no right or wrong answers.", "about 1 minute"),
}

data class Option(val label: String, val code: Int)

sealed interface Question {
    val id: String
    val section: Section
    val prompt: String
    val helper: String?
}

data class ChoiceQuestion(
    override val id: String,
    override val section: Section,
    override val prompt: String,
    override val helper: String? = null,
    val options: List<Option>,
    /** Short options (e.g. 1-10) are shown as a compact grid. */
    val compact: Boolean = false,
) : Question

data class NumberQuestion(
    override val id: String,
    override val section: Section,
    override val prompt: String,
    override val helper: String? = null,
    val min: Int,
    val max: Int,
    val default: Int,
    val unit: String,
) : Question

object QuestionBank {

    private fun scale(vararg labels: String, start: Int) = labels.mapIndexed { i, l -> Option(l, start + i) }

    private val neverToAlways = scale("Never", "Sometimes", "Often", "Always", start = 1)
    private val frequency = scale("Never", "Rarely", "Sometimes", "Often", "Very often", start = 0)
    private val agreement = scale("Strongly disagree", "Somewhat disagree", "Neutral", "Somewhat agree", "Strongly agree", start = 0)
    private val stateScale = scale("Not at all", "Somewhat", "Moderately", "Very much", start = 1)
    private val traitScale = scale("Almost never", "Sometimes", "Often", "Almost always", start = 1)

    private val about: List<Question> = listOf(
        NumberQuestion("Age", Section.ABOUT, "How old are you?", null, min = 16, max = 60, default = 20, unit = "years"),
        ChoiceQuestion(
            "Semester", Section.ABOUT, "How many semesters have you completed?",
            "Count your current semester if you're in the middle of it.",
            options = (1..10).map { Option(if (it == 10) "10+" else "$it", it) }, compact = true,
        ),
        ChoiceQuestion(
            "Living_Arrangement", Section.ABOUT, "Who do you live with at the moment?",
            options = listOf(
                Option("Both parents", 1), Option("One parent", 2), Option("Relatives", 3),
                Option("Roommate(s)", 4), Option("Partner", 5), Option("I live alone", 6),
            ),
        ),
        ChoiceQuestion(
            "Family_Expression", Section.ABOUT, "How often can you express your opinions and feelings within your family?",
            options = neverToAlways,
        ),
        ChoiceQuestion(
            "Family_Activities", Section.ABOUT, "How often does your family do things together?",
            options = neverToAlways,
        ),
        ChoiceQuestion(
            "Time_First_Cellphone", Section.ABOUT, "How long have you had your own mobile phone?",
            options = listOf(
                Option("Less than 6 months", 1), Option("6 months to 1 year", 2), Option("1 to 3 years", 3),
                Option("3 to 6 years", 4), Option("More than 6 years", 5),
            ),
        ),
    )

    private val tmdPrompts = listOf(
        "I've been called out or warned about my heavy phone use.",
        "I set a spending limit for my phone, but I always run out of credit or bundles before the month ends.",
        "I've argued with my parents or relatives about the cost of my phone.",
        "I spend more time than I'd like calling, texting or chatting on my phone.",
        "I send more than five messages a day on my phone.",
        "I've gone to bed later or slept less because I was using my phone.",
        "I spend more money on my phone (airtime, bundles, apps) than I planned.",
        "When I'm bored, I use my phone.",
        "I use my phone when it's inappropriate, such as while eating or talking to people.",
        "I've been criticised for spending too much money or time on my phone.",
        "When I haven't used my phone for a while, I feel the need to call, text or chat with someone.",
        "Since I've had a phone, I make more calls.",
        "If my phone broke and took a long time to fix, I'd feel really bad.",
        "I find myself needing to use my phone more and more.",
        "If I don't have my phone with me, I feel bad.",
        "When my phone is in my hands, I can't stop using it.",
        "Since I've had a phone, I send more texts and chat messages.",
        "As soon as I wake up, I check whether anyone has called or messaged me.",
        "I spend more money on my phone now than when I first got it.",
        "I don't think I could go a week without my phone.",
        "When I feel lonely, I call or message someone.",
        "Right now, I'd grab my phone to send a message or make a call.",
    )

    private val phone: List<Question> = tmdPrompts.mapIndexed { i, prompt ->
        ChoiceQuestion(
            id = "TMD_Q${i + 1}",
            section = Section.PHONE,
            prompt = prompt,
            helper = if (i < 10) "How often does this happen?" else "How much do you agree?",
            options = if (i < 10) frequency else agreement,
        )
    }

    private val stateItems = listOf(
        1 to "I feel calm.",
        2 to "I feel secure.",
        7 to "I'm worrying about things that might go wrong.",
        9 to "I feel frightened.",
        17 to "I feel worried.",
    )
    private val traitItems = listOf(
        21 to "I feel pleasant.",
        24 to "I wish I could be as happy as others seem to be.",
        29 to "I worry too much over things that don't really matter.",
        31 to "I have disturbing thoughts.",
        35 to "I feel inadequate.",
        37 to "Unimportant thoughts run through my mind and bother me.",
        38 to "I take disappointments so hard that I can't put them out of my mind.",
        40 to "I get tense or upset when I think about my recent concerns.",
    )

    private val feelings: List<Question> =
        stateItems.map { (n, p) -> ChoiceQuestion("STAI_item_$n", Section.FEELINGS, p, "Right now, at this moment…", stateScale) } +
            traitItems.map { (n, p) -> ChoiceQuestion("STAI_item_$n", Section.FEELINGS, p, "In general, how often…", traitScale) }

    val all: List<Question> = about + phone + feelings

    val tmdIds: List<String> = phone.map { it.id }

    /** Phone-dependence band from the TMD codebook (0-29 Absence, 30-38 Low, 39-48 Medium, 49-88 High). */
    fun tmdBand(total: Int): String = when {
        total <= 29 -> "Very low"
        total <= 38 -> "Low"
        total <= 48 -> "Moderate"
        else -> "High"
    }

    /** Turns raw answers into the model's input map; TMD_Total is the sum of the 22 TMD answers. */
    fun modelInputs(answers: Map<String, Int>, features: List<String>): Map<String, Double> =
        features.associateWith { f ->
            if (f == "TMD_Total") tmdIds.sumOf { answers.getValue(it) }.toDouble()
            else answers[f]?.toDouble() ?: error("No question answers model input '$f'")
        }

    /** Model inputs that the question bank cannot provide (should be empty). */
    fun uncovered(features: List<String>): List<String> {
        val ids = all.map { it.id }.toSet()
        return features.filter { it != "TMD_Total" && it !in ids }
    }
}
