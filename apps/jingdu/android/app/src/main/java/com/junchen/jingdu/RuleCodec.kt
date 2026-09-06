package com.junchen.jingdu

internal object RuleCodec {
    private const val RECORD = '\u001e'
    private const val FIELD = '\u001f'
    private const val GLOB_MARKER = "@g"
    private const val MAX_RULES = 500
    private const val MAX_FIELD_CHARS = 1024

    fun parse(packed: String): List<RepairRule> {
        if (packed.isEmpty()) return emptyList()
        return packed.split(RECORD).mapNotNull { record ->
            val fields = record.split(FIELD, limit = 3)
            when {
                fields.size == 3 && fields[0] == GLOB_MARKER && valid(fields[1], fields[2]) ->
                    RepairRule(fields[1], fields[2], RepairRuleMode.LINE_GLOB)
                fields.size >= 2 && valid(fields[0], fields[1]) ->
                    RepairRule(fields[0], fields[1], RepairRuleMode.LITERAL)
                else -> null
            }
        }.take(MAX_RULES)
    }

    fun pack(rules: List<RepairRule>): String = rules
        .asSequence()
        .filter { it.find.isNotBlank() && valid(it.find, it.replacement) }
        .distinctBy { Triple(it.mode, it.find, it.replacement) }
        .take(MAX_RULES)
        .joinToString(RECORD.toString()) { rule ->
            when (rule.mode) {
                RepairRuleMode.LITERAL -> "${rule.find}$FIELD${rule.replacement}"
                RepairRuleMode.LINE_GLOB -> "$GLOB_MARKER$FIELD${rule.find}$FIELD${rule.replacement}"
            }
        }

    fun combined(bookRules: List<RepairRule>, globalRules: List<RepairRule>, proUnlocked: Boolean): List<RepairRule> =
        ((if (proUnlocked) globalRules else emptyList()) + bookRules)
            .distinctBy { Triple(it.mode, it.find, it.replacement) }
            .take(MAX_RULES)

    fun isValid(rule: RepairRule): Boolean = rule.find.isNotBlank() && valid(rule.find, rule.replacement)

    private fun valid(find: String, replacement: String): Boolean =
        find.length <= MAX_FIELD_CHARS &&
            replacement.length <= MAX_FIELD_CHARS &&
            find.none { it == RECORD || it == FIELD } &&
            replacement.none { it == RECORD || it == FIELD }
}
