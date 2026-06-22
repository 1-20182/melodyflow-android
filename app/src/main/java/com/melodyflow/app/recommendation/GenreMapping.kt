package com.melodyflow.app.recommendation

object GenreMapping {

    // Event type → music genre mapping
    val eventTypeToGenre = mapOf(
        "work" to listOf("轻音乐", "古典", "白噪音", "钢琴曲"),
        "leisure" to listOf("流行", "民谣", "爵士", "R&B"),
        "sport" to listOf("电子", "摇滚", "嘻哈", "高BGM"),
        "study" to listOf("古典", "轻音乐", "环境音", "器乐"),
        "sleep" to listOf("轻音乐", "冥想", "大自然", "ASMR"),
        "custom" to listOf("流行", "推荐")
    )

    // Season → music genre mapping
    val seasonToGenre = mapOf(
        "春季" to listOf("民谣", "轻音乐", "流行", "乡村"),
        "夏季" to listOf("电子", "摇滚", "流行", "雷鬼"),
        "秋季" to listOf("爵士", "古典", "民谣", "蓝调"),
        "冬季" to listOf("古典", "流行", "R&B", "圣诞")
    )

    fun getGenresForEventType(eventType: String): List<String> {
        return eventTypeToGenre[eventType] ?: eventTypeToGenre["custom"]!!
    }

    fun getGenresForSeason(season: String): List<String> {
        return seasonToGenre[season] ?: listOf("流行")
    }
}