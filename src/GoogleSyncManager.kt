package com.example.xgglassapp.logic

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.net.HttpURLConnection
import java.net.URL
import android.content.Intent
import android.net.Uri

/**
 * 升级版 Google Sync Manager - 完整 OAuth 2.0 集成
 * 
 * 使用方式：
 * 1. 首次授权: manager.initiateGoogleLogin(clientId, clientSecret)
 * 2. 用户授权后获得授权码，调用: manager.exchangeAuthorizationCode(code, clientId, clientSecret)
 * 3. 然后就可以调用 syncTasksToGoogle(), syncMemoriesToGoogle(), syncEventsToGoogle()
 */
class GoogleSyncManager {
    
    private val gson = Gson()
    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var tokenExpiresAt: Long = 0
    
    init {
        // 尝试从本地加载之前保存的令牌
        loadTokenFromFile()
    }

    // ==================== 第一阶段：获取授权 ====================

    /**
     * 第一步：打开浏览器，引导用户授权
     * 
     * 在 Activity 中调用：
     * val authUrl = manager.getAuthorizationUrl(clientId)
     * startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
     */
    fun getAuthorizationUrl(clientId: String): String {
        return "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=$clientId&" +
                "redirect_uri=urn:ietf:wg:oauth:2.0:oob&" +
                "response_type=code&" +
                "scope=https://www.googleapis.com/auth/drive%20" +
                "https://www.googleapis.com/auth/calendar%20" +
                "https://www.googleapis.com/auth/tasks&" +
                "access_type=offline&" +
                "prompt=consent"
    }

    /**
     * 第二步：用户获得授权码后，调用此方法交换访问令牌
     * 
     * 用户会在浏览器中看到：
     * "请输入此代码: 4/0AX4XfWh_xxxxxxxxxxxxxxxx"
     */
    fun exchangeAuthorizationCode(
        authCode: String,
        clientId: String,
        clientSecret: String
    ): Result<String> {
        return try {
            val url = URL("https://oauth2.googleapis.com/token")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true
            connection.connectTimeout = 10000
            
            // 构建请求参数
            val params = "client_id=$clientId&" +
                    "client_secret=$clientSecret&" +
                    "code=$authCode&" +
                    "grant_type=authorization_code&" +
                    "redirect_uri=urn:ietf:wg:oauth:2.0:oob"
            
            connection.outputStream.write(params.toByteArray())
            
            val responseCode = connection.responseCode
            
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = gson.fromJson(response, JsonObject::class.java)
                
                // 提取令牌
                accessToken = json.get("access_token").asString
                refreshToken = json.get("refresh_token").asString
                val expiresIn = json.get("expires_in").asLong
                tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000)
                
                // 保存到文件
                saveTokenToFile()
                
                println("✓ 成功获得访问令牌！")
                println("  Access Token: ${accessToken?.take(20)}...")
                println("  Expires in: $expiresIn 秒")
                
                Result.success("✓ 授权成功！")
            } else {
                val errorStream = connection.errorStream
                val error = errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                println("✗ 授权失败 (HTTP $responseCode)")
                println("  错误: $error")
                Result.failure(Exception("HTTP $responseCode: $error"))
            }
        } catch (e: Exception) {
            println("✗ 异常: ${e.message}")
            Result.failure(e)
        }
    }

    // ==================== 第二阶段：令牌管理 ====================

    /**
     * 检查令牌是否过期，如果过期则自动刷新
     */
    private fun ensureValidToken(clientId: String, clientSecret: String): Boolean {
        if (accessToken == null) {
            println("✗ 没有访问令牌，请先授权")
            return false
        }
        
        // 检查是否即将过期（提前 5 分钟刷新）
        if (System.currentTimeMillis() > tokenExpiresAt - (5 * 60 * 1000)) {
            println("⚠ 令牌即将过期，正在刷新...")
            return if (refreshToken != null) {
                refreshAccessToken(clientId, clientSecret)
            } else {
                println("✗ 没有 refresh token，需要重新授权")
                false
            }
        }
        
        return true
    }

    /**
     * 使用 refresh token 刷新访问令牌
     */
    private fun refreshAccessToken(clientId: String, clientSecret: String): Boolean {
        return try {
            val url = URL("https://oauth2.googleapis.com/token")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true
            
            val params = "client_id=$clientId&" +
                    "client_secret=$clientSecret&" +
                    "refresh_token=$refreshToken&" +
                    "grant_type=refresh_token"
            
            connection.outputStream.write(params.toByteArray())
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = gson.fromJson(response, JsonObject::class.java)
                
                accessToken = json.get("access_token").asString
                val expiresIn = json.get("expires_in").asLong
                tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000)
                
                saveTokenToFile()
                println("✓ 令牌已刷新")
                true
            } else {
                println("✗ 刷新失败，请重新授权")
                false
            }
        } catch (e: Exception) {
            println("✗ 刷新异常: ${e.message}")
            false
        }
    }

    /**
     * 保存令牌到本地文件
     */
    private fun saveTokenToFile() {
        try {
            val tokenFile = java.io.File(
                "${System.getProperty("user.home")}/.memory-sync/google_token.json"
            )
            tokenFile.parentFile?.mkdirs()
            
            val tokenJson = JsonObject()
            tokenJson.addProperty("access_token", accessToken)
            tokenJson.addProperty("refresh_token", refreshToken)
            tokenJson.addProperty("expires_at", tokenExpiresAt)
            
            tokenFile.writeText(gson.toJson(tokenJson))
            println("✓ 令牌已保存到文件")
        } catch (e: Exception) {
            println("✗ 保存令牌失败: ${e.message}")
        }
    }

    /**
     * 从本地文件加载令牌
     */
    private fun loadTokenFromFile() {
        try {
            val tokenFile = java.io.File(
                "${System.getProperty("user.home")}/.memory-sync/google_token.json"
            )
            
            if (tokenFile.exists()) {
                val json = gson.fromJson(tokenFile.readText(), JsonObject::class.java)
                accessToken = json.get("access_token")?.asString
                refreshToken = json.get("refresh_token")?.asString
                tokenExpiresAt = json.get("expires_at")?.asLong ?: 0
                println("✓ 已从文件加载令牌")
            }
        } catch (e: Exception) {
            println("⚠ 无法加载令牌: ${e.message}")
        }
    }

    /**
     * 清除保存的令牌（用于登出）
     */
    fun logout() {
        try {
            val tokenFile = java.io.File(
                "${System.getProperty("user.home")}/.memory-sync/google_token.json"
            )
            if (tokenFile.exists()) {
                tokenFile.delete()
            }
            accessToken = null
            refreshToken = null
            tokenExpiresAt = 0
            println("✓ 已登出")
        } catch (e: Exception) {
            println("✗ 登出失败: ${e.message}")
        }
    }

    // ==================== 第三阶段：调用 Google APIs ====================

    /**
     * 同步任务到 Google Tasks
     */
    fun syncTasksToGoogle(
        tasks: List<TaskItem>,
        clientId: String? = null,
        clientSecret: String? = null
    ): SyncResult {
        if (clientId != null && clientSecret != null && !ensureValidToken(clientId, clientSecret)) {
            return SyncResult(0, tasks.size, "缺少有效的访问令牌")
        }
        
        if (accessToken == null) {
            println("⚠ 没有访问令牌，本地保存任务")
            logTasksLocally(tasks)
            return SyncResult(0, tasks.size, "本地保存（未授权）")
        }

        var successCount = 0
        var failureCount = 0
        val errors = mutableListOf<String>()
        
        println("\n📋 正在同步任务到 Google Tasks...")
        println("━".repeat(50))
        
        tasks.forEach { task ->
            try {
                if (createTaskInGoogle(task)) {
                    println("  ✓ [任务] ${task.title}")
                    successCount++
                } else {
                    println("  ✗ [任务] ${task.title}")
                    failureCount++
                }
            } catch (e: Exception) {
                println("  ✗ [任务] ${task.title} - ${e.message}")
                failureCount++
                errors.add("${task.title}: ${e.message}")
            }
        }
        
        println("━".repeat(50))
        println("结果: $successCount 成功, $failureCount 失败\n")
        
        return SyncResult(successCount, failureCount, errors.joinToString("\n"))
    }

    private fun createTaskInGoogle(task: TaskItem): Boolean {
        return try {
            val url = URL("https://www.googleapis.com/tasks/v1/lists/@default/tasks")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            
            val taskJson = JsonObject()
            taskJson.addProperty("title", task.title)
            taskJson.addProperty("notes", task.description)
            if (!task.dueDate.isNullOrBlank()) {
                taskJson.addProperty("due", "${task.dueDate}T00:00:00Z")
            }
            
            val requestBody = gson.toJson(taskJson)
            connection.outputStream.write(requestBody.toByteArray())
            
            val responseCode = connection.responseCode
            responseCode in 200..299
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 同步记忆到 Google Drive
     */
    fun syncMemoriesToGoogle(
        memories: List<MemoryItem>,
        clientId: String? = null,
        clientSecret: String? = null
    ): SyncResult {
        if (clientId != null && clientSecret != null && !ensureValidToken(clientId, clientSecret)) {
            return SyncResult(0, memories.size, "缺少有效的访问令牌")
        }

        if (accessToken == null) {
            println("⚠ 没有访问令牌，本地保存记忆")
            logMemoriesLocally(memories)
            return SyncResult(0, memories.size, "本地保存（未授权）")
        }

        var successCount = 0
        var failureCount = 0
        
        println("\n📄 正在同步记忆到 Google Drive...")
        println("━".repeat(50))
        
        memories.forEach { memory ->
            try {
                if (createMemoryInGoogle(memory)) {
                    println("  ✓ [记忆] ${memory.title}")
                    successCount++
                } else {
                    println("  ✗ [记忆] ${memory.title}")
                    failureCount++
                }
            } catch (e: Exception) {
                println("  ✗ [记忆] ${memory.title}")
                failureCount++
            }
        }
        
        println("━".repeat(50))
        println("结果: $successCount 成功, $failureCount 失败\n")
        
        return SyncResult(successCount, failureCount, null)
    }

    private fun createMemoryInGoogle(memory: MemoryItem): Boolean {
        return try {
            // 创建文件元数据
            val url = URL("https://www.googleapis.com/drive/v3/files")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            
            val fileMetadata = JsonObject()
            fileMetadata.addProperty("name", "Memory_${memory.title}")
            fileMetadata.addProperty("mimeType", "text/plain")
            
            connection.outputStream.write(gson.toJson(fileMetadata).toByteArray())
            
            val responseCode = connection.responseCode
            responseCode in 200..299
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 同步事件到 Google Calendar
     */
    fun syncEventsToGoogle(
        events: List<EventItem>,
        clientId: String? = null,
        clientSecret: String? = null
    ): SyncResult {
        if (clientId != null && clientSecret != null && !ensureValidToken(clientId, clientSecret)) {
            return SyncResult(0, events.size, "缺少有效的访问令牌")
        }

        if (accessToken == null) {
            println("⚠ 没有访问令牌，本地保存事件")
            logEventsLocally(events)
            return SyncResult(0, events.size, "本地保存（未授权）")
        }

        var successCount = 0
        var failureCount = 0
        
        println("\n📅 正在同步事件到 Google Calendar...")
        println("━".repeat(50))
        
        events.forEach { event ->
            try {
                if (createEventInGoogle(event)) {
                    println("  ✓ [事件] ${event.title}")
                    successCount++
                } else {
                    println("  ✗ [事件] ${event.title}")
                    failureCount++
                }
            } catch (e: Exception) {
                println("  ✗ [事件] ${event.title}")
                failureCount++
            }
        }
        
        println("━".repeat(50))
        println("结果: $successCount 成功, $failureCount 失败\n")
        
        return SyncResult(successCount, failureCount, null)
    }

    private fun createEventInGoogle(event: EventItem): Boolean {
        return try {
            val url = URL("https://www.googleapis.com/calendar/v3/calendars/primary/events")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            
            val eventJson = JsonObject()
            eventJson.addProperty("summary", event.title)
            eventJson.addProperty("description", event.description)
            if (!event.location.isNullOrBlank()) {
                eventJson.addProperty("location", event.location)
            }
            
            val start = JsonObject()
            start.addProperty("dateTime", event.startTime)
            eventJson.add("start", start)
            
            val end = JsonObject()
            end.addProperty("dateTime", event.endTime)
            eventJson.add("end", end)
            
            connection.outputStream.write(gson.toJson(eventJson).toByteArray())
            
            val responseCode = connection.responseCode
            responseCode in 200..299
        } catch (e: Exception) {
            false
        }
    }

    // ==================== 本地存储备选方案 ====================

    private fun logTasksLocally(tasks: List<TaskItem>) {
        try {
            val file = java.io.File("${System.getProperty("user.home")}/memory-sync/tasks.json")
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(tasks))
            println("✓ 任务已保存到本地: ${file.absolutePath}")
        } catch (e: Exception) {
            println("✗ 保存失败: ${e.message}")
        }
    }

    private fun logMemoriesLocally(memories: List<MemoryItem>) {
        try {
            val file = java.io.File("${System.getProperty("user.home")}/memory-sync/memories.json")
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(memories))
            println("✓ 记忆已保存到本地: ${file.absolutePath}")
        } catch (e: Exception) {
            println("✗ 保存失败: ${e.message}")
        }
    }

    private fun logEventsLocally(events: List<EventItem>) {
        try {
            val file = java.io.File("${System.getProperty("user.home")}/memory-sync/events.json")
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(events))
            println("✓ 事件已保存到本地: ${file.absolutePath}")
        } catch (e: Exception) {
            println("✗ 保存失败: ${e.message}")
        }
    }

    /**
     * 同步结果数据类
     */
    data class SyncResult(
        val successCount: Int,
        val failureCount: Int,
        val errorMessage: String?
    )

    /**
     * 获取授权状态
     */
    fun getAuthStatus(): String {
        return when {
            accessToken == null -> "❌ 未授权"
            System.currentTimeMillis() > tokenExpiresAt -> "⚠️ 令牌已过期"
            System.currentTimeMillis() > tokenExpiresAt - (5 * 60 * 1000) -> "⚠️ 令牌即将过期"
            else -> "✅ 已授权"
        }
    }
}
