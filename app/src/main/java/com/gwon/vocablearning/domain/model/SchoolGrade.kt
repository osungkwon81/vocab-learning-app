package com.gwon.vocablearning.domain.model

enum class SchoolGrade(
    val code: String,
    val label: String,
    val fileKey: String,
) {
    MIDDLE_1("middle1", "중1", "english_middle1"),
    MIDDLE_2("middle2", "중2", "english_middle2"),
    MIDDLE_3("middle3", "중3", "english_middle3"),
    HIGH_1("high1", "고1", "english_high1"),
    HIGH_2("high2", "고2", "english_high2"),
    HIGH_3("high3", "고3", "english_high3"),
    ;

    val assetPath: String = "data/en/$fileKey.json"
    val remotePath: String = "catalog/en/$fileKey.json"

    companion object {
        fun fromCode(code: String): SchoolGrade =
            entries.firstOrNull { it.code == code } ?: HIGH_3
    }
}

