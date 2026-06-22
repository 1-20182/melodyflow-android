package com.melodyflow.app.recommendation

data class HolidayInfo(
    val name: String,
    val month: Int,
    val day: Int,
    val genre: String,
    val description: String
)

object HolidayDatabase {
    val holidays = listOf(
        HolidayInfo("元旦", 1, 1, "流行/欢快", "新年新气象"),
        HolidayInfo("情人节", 2, 14, "浪漫/情歌", "爱的旋律"),
        HolidayInfo("妇女节", 3, 8, "温馨/流行", "致敬女性"),
        HolidayInfo("愚人节", 4, 1, "轻快/搞笑", "轻松一下"),
        HolidayInfo("劳动节", 5, 1, "流行/摇滚", "假期快乐"),
        HolidayInfo("儿童节", 6, 1, "儿歌/动漫", "童心未泯"),
        HolidayInfo("国庆节", 10, 1, "红歌/民谣", "祖国万岁"),
        HolidayInfo("平安夜", 12, 24, "圣诞/古典", "平安喜乐"),
        HolidayInfo("圣诞节", 12, 25, "圣诞/流行", "圣诞快乐"),
        HolidayInfo("春节", 1, 1, "喜庆/民乐", "新春大吉")
    )

    fun getHoliday(month: Int, day: Int): HolidayInfo? {
        return holidays.find { it.month == month && it.day == day }
    }
}