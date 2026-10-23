package com.juice.core.character

enum class Shape { SQUARE, CIRCLE, TRIANGLE }

/** The shapes a character's face is drawn with. */
data class Face(val eye: Shape, val mouth: Shape)

/** How one facial feature is drawn: [scale] and [squash] (vertical scale) are factors, [rotation] is in degrees, offsets are in face-relative units (+y is down). */
data class FeatureTransform(
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val squash: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

data class ExpressionTransform(val eyes: FeatureTransform, val mouth: FeatureTransform)

enum class Expression(val transform: ExpressionTransform) {
    NEUTRAL(ExpressionTransform(FeatureTransform(), FeatureTransform())),
    HAPPY(ExpressionTransform(FeatureTransform(squash = 0.6f, offsetY = -0.05f), FeatureTransform(scale = 1.3f, rotation = 180f, offsetY = -0.05f))),
    SAD(ExpressionTransform(FeatureTransform(rotation = -15f, squash = 0.8f, offsetY = 0.05f), FeatureTransform(scale = 0.8f, offsetY = 0.1f))),
    SCARED(ExpressionTransform(FeatureTransform(scale = 1.4f, offsetY = -0.05f), FeatureTransform(scale = 0.9f, squash = 1.4f, offsetY = 0.08f))),
    ANGRY(ExpressionTransform(FeatureTransform(rotation = 20f, squash = 0.7f), FeatureTransform(scale = 1.1f, squash = 0.7f, offsetY = 0.05f))),
    SURPRISED(ExpressionTransform(FeatureTransform(scale = 1.5f), FeatureTransform(scale = 1.2f, squash = 1.6f, offsetY = 0.05f))),
    CHEEKY(ExpressionTransform(FeatureTransform(rotation = 10f, squash = 0.5f, offsetX = 0.05f), FeatureTransform(rotation = 15f, offsetX = 0.12f, offsetY = -0.02f)));

    companion object {
        private val leadingTag = Regex("""^\s*\[([A-Za-z]+)]\s*""")

        /** Reads a leading `[expression]` tag off [reply] and strips it; a missing or unknown tag gives [NEUTRAL] and the full text. */
        fun parse(reply: String): Pair<Expression, String> {
            val match = leadingTag.find(reply) ?: return NEUTRAL to reply
            val expression = entries.firstOrNull { it.name.equals(match.groupValues[1], ignoreCase = true) } ?: return NEUTRAL to reply
            return expression to reply.substring(match.range.last + 1)
        }
    }
}
