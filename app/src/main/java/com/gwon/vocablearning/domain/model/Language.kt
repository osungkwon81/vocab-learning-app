package com.gwon.vocablearning.domain.model

enum class Language(
    val code: String,
    val label: String,
) {
    ENGLISH("en", "영어"),
    JAPANESE("ja", "일본어"),
    CHINESE("zh", "중국어"),
    ;

    companion object {
        fun fromCode(code: String): Language =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

