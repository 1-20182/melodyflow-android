package com.melodyflow.app.service

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.melodyflow.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

class AIService(private val config: AIConfig) {

    private val client: OkHttpClient
    private val gson = Gson()

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 测试AI连接
     */
    suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Testing connection to ${config.provider} at ${config.apiUrl}")
            Log.d(TAG, "Using model: ${config.model}")
            
            val response = sendChatRequest(
                messages = listOf(
                    mapOf("role" to "system", "content" to "You are a helpful assistant."),
                    mapOf("role" to "user", "content" to "Hello")
                ),
                maxTokens = 10
            )
            
            if (response != null) {
                Log.d(TAG, "Connection test successful")
                Pair(true, "连接成功")
            } else {
                Log.e(TAG, "Connection test failed: empty response")
                Pair(false, "连接失败：空响应")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Connection test failed - IO error: ${e.message}", e)
            Pair(false, "网络错误：${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed - error: ${e.message}", e)
            Pair(false, "错误：${e.message}")
        }
    }

    /**
     * 获取可用模型列表
     */
    suspend fun getAvailableModels(): Pair<List<String>, String?> = withContext(Dispatchers.IO) {
        try {
            // 构建模型列表API地址
            val modelsUrl = buildModelsUrl()
            Log.d(TAG, "Fetching models from URL: $modelsUrl")
            Log.d(TAG, "Original API URL: ${config.apiUrl}")
            
            val requestBuilder = Request.Builder()
                .url(modelsUrl)
                .get()
            
            // 添加认证头
            addAuthHeaders(requestBuilder)
            
            val request = requestBuilder.build()
            
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                
                if (!response.isSuccessful) {
                    // 特殊处理常见错误码
                    val errorMsg = when (response.code) {
                        401 -> "API Key无效或已过期 (HTTP 401)"
                        403 -> "没有权限访问模型列表 (HTTP 403)"
                        404 -> "模型列表API不存在 (HTTP 404)，此API可能不支持获取模型列表"
                        500, 502, 503 -> "服务器错误 (HTTP ${response.code})，请稍后重试"
                        else -> "获取模型列表失败: HTTP ${response.code}"
                    }
                    Log.e(TAG, "$errorMsg, body: $responseBody")
                    return@withContext Pair(emptyList<String>(), errorMsg)
                }
                
                if (responseBody == null) {
                    return@withContext Pair(emptyList<String>(), "空响应")
                }
                
                // 检查返回的是否是HTML
                if (responseBody.trim().startsWith("<") || responseBody.contains("<!doctype")) {
                    Log.e(TAG, "Received HTML instead of JSON")
                    return@withContext Pair(emptyList<String>(), "API返回了HTML页面，请检查API地址是否正确")
                }
                
                val models = parseModelsResponse(responseBody)
                if (models.isEmpty()) {
                    return@withContext Pair(emptyList<String>(), "未找到可用模型")
                }
                
                Pair(models, null)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to fetch models: ${e.message}", e)
            Pair(emptyList<String>(), "网络错误: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch models: ${e.message}", e)
            Pair(emptyList<String>(), "错误: ${e.message}")
        }
    }
    
    /**
     * 构建模型列表API地址
     * 参考 AstrBot 实现：替换 chat/completions 为 models
     */
    private fun buildModelsUrl(): String {
        val baseUrl = config.apiUrl.trim()
        
        // 如果 URL 包含 /chat/completions，替换为 /models
        if (baseUrl.contains("/chat/completions")) {
            return baseUrl.replace("/chat/completions", "/models")
        }
        
        // 如果 URL 以 /v1 或 /v2 结尾，添加 /models
        if (baseUrl.endsWith("/v1") || baseUrl.endsWith("/v2")) {
            return "$baseUrl/models"
        }
        
        // 如果 URL 以 / 结尾，添加 v1/models
        if (baseUrl.endsWith("/")) {
            return "${baseUrl}v1/models"
        }
        
        // 其他情况，添加 /v1/models
        return "$baseUrl/v1/models"
    }
    
    /**
     * 添加认证头
     */
    private fun addAuthHeaders(requestBuilder: Request.Builder) {
        when (config.provider) {
            AIProvider.ANTHROPIC -> {
                requestBuilder.header("x-api-key", config.apiKey)
                requestBuilder.header("anthropic-version", "2023-06-01")
            }
            else -> {
                requestBuilder.header("Authorization", "Bearer ${config.apiKey}")
            }
        }
        requestBuilder.header("Content-Type", "application/json")
    }
    
    /**
     * 解析模型列表响应
     */
    private fun parseModelsResponse(response: String): List<String> {
        val models = mutableListOf<String>()
        
        try {
            val jsonElement = gson.fromJson(response, com.google.gson.JsonElement::class.java)
            if (!jsonElement.isJsonObject) return models
            
            val jsonObject = jsonElement.asJsonObject
            
            // OpenAI格式: data[].id
            if (jsonObject.has("data")) {
                val dataArray = jsonObject.getAsJsonArray("data")
                dataArray?.forEach { element ->
                    if (element.isJsonObject) {
                        val modelId = element.asJsonObject.get("id")?.asString
                        if (!modelId.isNullOrBlank()) {
                            models.add(modelId)
                        }
                    }
                }
            }
            
            // 其他格式: models[] 或 object字段
            if (models.isEmpty() && jsonObject.has("models")) {
                val modelsArray = jsonObject.getAsJsonArray("models")
                modelsArray?.forEach { element ->
                    if (element.isJsonPrimitive) {
                        models.add(element.asString)
                    } else if (element.isJsonObject) {
                        val modelId = element.asJsonObject.get("id")?.asString
                            ?: element.asJsonObject.get("name")?.asString
                        if (!modelId.isNullOrBlank()) {
                            models.add(modelId)
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse models response: $response", e)
        }
        
        return models.sorted()
    }

    /**
     * 获取AI推荐歌曲
     */
    suspend fun getRecommendations(request: AIRecommendationRequest): AIRecommendationResponse? =
        withContext(Dispatchers.IO) {
            try {
                val prompt = buildRecommendationPrompt(request)
                Log.d(TAG, "Recommendation prompt: $prompt")

                val messages = listOf(
                    mapOf("role" to "system", "content" to SYSTEM_PROMPT),
                    mapOf("role" to "user", "content" to prompt)
                )

                val response = sendChatRequest(messages, maxTokens = 2000)
                response?.let { parseRecommendationResponse(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Get recommendations failed", e)
                null
            }
        }

    /**
     * 发送聊天请求 - 兼容多种AI格式
     */
    private suspend fun sendChatRequest(
        messages: List<Map<String, String>>,
        maxTokens: Int = 1000
    ): String? = withContext(Dispatchers.IO) {
        // 自动补全API路径
        val actualUrl = buildChatApiUrl(config.apiUrl)
        Log.d(TAG, "Using API URL: $actualUrl")
        
        val requestBody = when (config.provider) {
            AIProvider.ANTHROPIC -> buildAnthropicRequestBody(messages, maxTokens)
            else -> {
                // 检测是否为讯飞星火API
                if (actualUrl.contains("xf-yun.com") || actualUrl.contains("xunfei")) {
                    buildXunfeiRequestBody(messages, maxTokens)
                } else {
                    buildOpenAICompatibleRequestBody(messages, maxTokens)
                }
            }
        }

        val mediaType = "application/json".toMediaType()
        val body = RequestBody.create(mediaType, requestBody.toString())

        val requestBuilder = Request.Builder()
            .url(actualUrl)
            .post(body)

        // 添加认证头
        when (config.provider) {
            AIProvider.ANTHROPIC -> {
                requestBuilder.header("x-api-key", config.apiKey)
                requestBuilder.header("anthropic-version", "2023-06-01")
            }
            else -> {
                requestBuilder.header("Authorization", "Bearer ${config.apiKey}")
            }
        }

        requestBuilder.header("Content-Type", "application/json")

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "API error: HTTP ${response.code}, body: $responseBody")
                throw IOException("HTTP ${response.code}: ${responseBody ?: "Unknown error"}")
            }

            // 检查返回的是否是HTML
            if (responseBody != null && (responseBody.trim().startsWith("<") || responseBody.contains("<!doctype"))) {
                Log.e(TAG, "Received HTML instead of JSON. Please check the API URL.")
                throw IOException("API返回了HTML页面而非JSON，请检查API地址是否正确")
            }

            responseBody?.let { extractContentFromResponse(it) }
        }
    }
    
    /**
     * 构建聊天API URL
     * 参考 AstrBot 实现：直接使用用户提供的 URL，让服务端处理路径
     */
    private fun buildChatApiUrl(inputUrl: String): String {
        val trimmedUrl = inputUrl.trim()
        
        // 如果用户已经提供了完整路径，直接使用
        if (trimmedUrl.contains("/chat/completions")) {
            return trimmedUrl
        }
        
        // 如果 URL 以 /v1 或 /v2 结尾，添加 /chat/completions
        if (trimmedUrl.endsWith("/v1") || trimmedUrl.endsWith("/v2")) {
            return "$trimmedUrl/chat/completions"
        }
        
        // 如果 URL 以 / 结尾，添加 v1/chat/completions
        if (trimmedUrl.endsWith("/")) {
            return "${trimmedUrl}v1/chat/completions"
        }
        
        // 其他情况，添加 /v1/chat/completions
        return "$trimmedUrl/v1/chat/completions"
    }

    /**
     * 构建OpenAI兼容格式的请求体（包括AstrBot）
     */
    private fun buildOpenAICompatibleRequestBody(
        messages: List<Map<String, String>>,
        maxTokens: Int
    ): JsonObject {
        return JsonObject().apply {
            addProperty("model", config.model)
            add("messages", gson.toJsonTree(messages))
            addProperty("max_tokens", maxTokens)
            addProperty("temperature", 0.7)
        }
    }

    /**
     * 构建Anthropic格式的请求体
     */
    private fun buildAnthropicRequestBody(
        messages: List<Map<String, String>>,
        maxTokens: Int
    ): JsonObject {
        val systemMessage = messages.find { it["role"] == "system" }?.get("content") ?: ""
        val userMessages = messages.filter { it["role"] != "system" }

        return JsonObject().apply {
            addProperty("model", config.model)
            addProperty("max_tokens", maxTokens)
            addProperty("system", systemMessage)
            add("messages", gson.toJsonTree(userMessages))
        }
    }

    /**
     * 构建讯飞星火格式的请求体
     */
    private fun buildXunfeiRequestBody(
        messages: List<Map<String, String>>,
        maxTokens: Int
    ): JsonObject {
        // 讯飞星火API格式
        val messageArray = com.google.gson.JsonArray()
        messages.forEach { msg ->
            val msgObject = JsonObject().apply {
                addProperty("role", msg["role"])
                addProperty("content", msg["content"])
            }
            messageArray.add(msgObject)
        }

        return JsonObject().apply {
            addProperty("model", config.model)
            add("messages", messageArray)
            addProperty("max_tokens", maxTokens)
            addProperty("temperature", 0.7)
        }
    }

    /**
     * 从响应中提取内容
     */
    private fun extractContentFromResponse(response: String): String? {
        return try {
            // 首先检查响应是否为JSON对象
            val jsonElement = gson.fromJson(response, com.google.gson.JsonElement::class.java)
            
            if (!jsonElement.isJsonObject) {
                Log.e(TAG, "Response is not a JSON object: $response")
                return null
            }
            
            val jsonObject = jsonElement.asJsonObject

            // 尝试OpenAI/AstrBot格式
            val choices = jsonObject.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val firstChoice = choices[0].asJsonObject
                val message = firstChoice.getAsJsonObject("message")
                return message?.get("content")?.asString
            }

            // 尝试Anthropic格式
            val content = jsonObject.getAsJsonArray("content")
            if (content != null && content.size() > 0) {
                return content[0].asJsonObject.get("text")?.asString
            }

            // 尝试Gemini格式
            val candidates = jsonObject.getAsJsonArray("candidates")
            if (candidates != null && candidates.size() > 0) {
                val parts = candidates[0].asJsonObject
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                if (parts != null && parts.size() > 0) {
                    return parts[0].asJsonObject.get("text")?.asString
                }
            }
            
            // 尝试讯飞星火格式 (payload.choices.text.content)
            val payload = jsonObject.getAsJsonObject("payload")
            if (payload != null) {
                val payloadChoices = payload.getAsJsonArray("choices")
                if (payloadChoices != null && payloadChoices.size() > 0) {
                    val text = payloadChoices[0].asJsonObject.getAsJsonObject("text")
                    return text?.get("content")?.asString
                }
            }
            
            // 尝试直接获取content字段
            if (jsonObject.has("content")) {
                val contentElement = jsonObject.get("content")
                if (contentElement.isJsonPrimitive) {
                    return contentElement.asString
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response: $response", e)
            null
        }
    }

    /**
     * 构建推荐提示词
     */
    private fun buildRecommendationPrompt(request: AIRecommendationRequest): String {
        val sb = StringBuilder()

        // 用户收藏的歌曲
        if (request.favoriteSongs.isNotEmpty()) {
            sb.appendLine("我喜欢的歌曲：")
            request.favoriteSongs.take(20).forEach { song ->
                sb.appendLine("- ${song.name} - ${song.artist}")
            }
            sb.appendLine()
        }

        // 最近播放的歌曲
        if (request.recentHistory.isNotEmpty()) {
            sb.appendLine("我最近听的歌曲：")
            request.recentHistory.take(15).forEach { song ->
                sb.appendLine("- ${song.name} - ${song.artist}")
            }
            sb.appendLine()
        }

        // 用户的额外输入
        if (!request.userPrompt.isNullOrBlank()) {
            sb.appendLine("我的需求：${request.userPrompt}")
            sb.appendLine()
        }

        sb.appendLine("请根据以上信息，为我推荐${request.limit}首歌曲。要求：")
        sb.appendLine("1. 推荐的歌曲要符合我的音乐品味")
        sb.appendLine("2. 可以包含一些风格相似但我可能没听过的歌曲")
        sb.appendLine("3. 每首歌曲简要说明推荐理由")
        sb.appendLine()
        sb.appendLine("请严格按照以下JSON格式返回：")
        sb.appendLine("""
            {
              "playlistName": "推荐歌单名称",
              "explanation": "整体推荐说明",
              "recommendations": [
                {
                  "songName": "歌曲名",
                  "artist": "歌手",
                  "album": "专辑名（可选）",
                  "reason": "推荐理由"
                }
              ]
            }
        """.trimIndent())

        return sb.toString()
    }

    /**
     * 解析推荐响应
     */
    private fun parseRecommendationResponse(response: String): AIRecommendationResponse? {
        return try {
            // 尝试从响应中提取JSON
            val jsonPattern = "\\{[\\s\\S]*\\}".toRegex()
            val jsonMatch = jsonPattern.find(response)
            val jsonString = jsonMatch?.value ?: response

            val jsonObject = gson.fromJson(jsonString, JsonObject::class.java)

            val playlistName = jsonObject.get("playlistName")?.asString ?: "AI推荐歌单"
            val explanation = jsonObject.get("explanation")?.asString ?: ""

            val recommendations = mutableListOf<AIRecommendedSong>()
            val recsArray = jsonObject.getAsJsonArray("recommendations")

            recsArray?.forEach { element ->
                val obj = element.asJsonObject
                recommendations.add(
                    AIRecommendedSong(
                        songName = obj.get("songName")?.asString ?: "",
                        artist = obj.get("artist")?.asString ?: "",
                        album = obj.get("album")?.asString,
                        reason = obj.get("reason")?.asString
                    )
                )
            }

            AIRecommendationResponse(
                recommendations = recommendations,
                explanation = explanation,
                playlistName = playlistName
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse recommendation response", e)
            null
        }
    }

    companion object {
        private const val TAG = "AIService"

        private val SYSTEM_PROMPT = """
            你是一位专业的音乐推荐助手。你的任务是根据用户的听歌历史和喜好，推荐合适的歌曲。
            
            要求：
            1. 分析用户的音乐品味（流派、年代、情绪等）
            2. 推荐与用户喜好相符的歌曲
            3. 可以适当推荐一些相关风格的新歌曲
            4. 每首歌都要有具体的推荐理由
            5. 返回格式必须是有效的JSON
            
            注意：只返回JSON格式的数据，不要添加其他说明文字。
        """.trimIndent()
    }
}
