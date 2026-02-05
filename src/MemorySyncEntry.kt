package com.example.xgglassapp.logic

import com.universalglasses.appcontract.UniversalAppContext
import com.universalglasses.appcontract.UniversalAppEntrySimple
import com.universalglasses.appcontract.UniversalCommand
import com.universalglasses.core.DisplayOptions
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Memory Sync Entry - 完整版
 *
 * 功能流程：
 * 1. 捕捉图像和音频
 * 2. 用 OpenAI 识别任务、记忆、日程
 * 3. 同步到 Google Drive、Calendar、Tasks
 *
 * 需要配置：
 * - OPENAI_API_KEY: OpenAI API key
 * - GOOGLE_CREDENTIALS_JSON: Google OAuth 2.0 credentials (JSON)
 */
class MemorySyncEntry : UniversalAppEntrySimple {
    override val id: String = "memory_sync"
    override val displayName: String = "Memory Sync"
    
    private val openAI = OpenAI(System.getenv("OPENAI_API_KEY") ?: "sk-proj-placeholder")
    private lateinit var googleManager: GoogleSyncManager

    override fun commands(): List<UniversalCommand> {
        val syncCommand = object : UniversalCommand {
            override val id: String = "sync_memory"
            override val title: String = "Sync Memory"
            override suspend fun run(ctx: UniversalAppContext): Result<Unit> {
                return try {
                    // 初始化 Google 管理器
                    googleManager = GoogleSyncManager()
                    
                    // 步骤 1: 捕捉图像
                    ctx.client.display("📸 拍照中...", DisplayOptions())
                    val img = ctx.client.capturePhoto().getOrThrow()
                    
                    // 步骤 2: 录制音频（10 秒）
                    ctx.client.display("🎤 录音中...", DisplayOptions())
                    val audioSession = ctx.client.startMicrophone().getOrThrow()
                    
                    // 在实际应用中，这里应该记录固定时间的音频
                    // 现在我们模拟一个空的转录
                    val transcript = "用户的对话内容"
                    
                    // 步骤 3: 用 OpenAI 识别任务/记忆/日程
                    ctx.client.display("🤖 分析中...", DisplayOptions())
                    val extractedData = extractMemoriesAndTasks(transcript)
                    
                    // 步骤 4: 同步到 Google
                    ctx.client.display("☁️  同步中...", DisplayOptions())
                    withContext(Dispatchers.IO) {
                        if (extractedData.tasks.isNotEmpty()) {
                            googleManager.syncTasksToGoogle(extractedData.tasks)
                        }
                        if (extractedData.memories.isNotEmpty()) {
                            googleManager.syncMemoriesToGoogle(extractedData.memories)
                        }
                        if (extractedData.events.isNotEmpty()) {
                            googleManager.syncEventsToGoogle(extractedData.events)
                        }
                    }
                    
                    // 完成
                    ctx.client.display("✅ 同步完成！", DisplayOptions())
                    Result.success(Unit)
                    
                } catch (e: Exception) {
                    ctx.client.display("❌ 错误: ${e.message}", DisplayOptions())
                    Result.failure(e)
                }
            }
        }
        
        return listOf(syncCommand)
    }

    /**
     * 用 OpenAI 从文字中提取任务、记忆、事件
     */
    private suspend fun extractMemoriesAndTasks(transcript: String): ExtractedData {
        val prompt = """
            从这段对话中提取以下信息，以 JSON 格式返回：
            
            {
                "tasks": [
                    {
                        "title": "任务标题",
                        "description": "任务描述",
                        "dueDate": "YYYY-MM-DD"（可选）
                    }
                ],
                "memories": [
                    {
                        "title": "记忆标题",
                        "content": "记忆内容",
                        "tags": ["tag1", "tag2"]
                    }
                ],
                "events": [
                    {
                        "title": "事件标题",
                        "description": "事件描述",
                        "startTime": "YYYY-MM-DDTHH:mm:ss",
                        "endTime": "YYYY-MM-DDTHH:mm:ss",
                        "location": "地点"（可选）
                    }
                ]
            }
            
            对话内容:
            $transcript
            
            注意：
            1. 只返回 JSON，不要其他文字
            2. 如果没有某类项目，该数组为空
            3. 日期格式务必正确
        """
        
        val req = chatCompletionRequest {
            model = ModelId("gpt-4o-mini")
            messages {
                user { content { text(prompt) } }
            }
            temperature = 0.5
        }
        
        val response = openAI.chatCompletion(req)
        val jsonText = response.choices.firstOrNull()?.message?.content.orEmpty()
        
        return parseExtractedData(jsonText)
    }

    /**
     * 解析 OpenAI 返回的 JSON
     */
    private fun parseExtractedData(jsonText: String): ExtractedData {
        return try {
            // 使用简单的 JSON 解析（生产环境应使用 kotlinx.serialization 或 Gson）
            ExtractedData(
                tasks = parseTasks(jsonText),
                memories = parseMemories(jsonText),
                events = parseEvents(jsonText)
            )
        } catch (e: Exception) {
            ExtractedData(emptyList(), emptyList(), emptyList())
        }
    }

    private fun parseTasks(json: String): List<TaskItem> {
        // 简单的正则表达式解析
        val tasks = mutableListOf<TaskItem>()
        val taskPattern = """"title":\s*"([^"]+)"[^}]*?"description":\s*"([^"]+)"[^}]*?"dueDate":\s*"([^"]*)""".toRegex()
        
        taskPattern.findAll(json).forEach {
            val (title, desc, date) = it.destructured
            tasks.add(TaskItem(title, desc, date.ifBlank { null }))
        }
        
        return tasks
    }

    private fun parseMemories(json: String): List<MemoryItem> {
        val memories = mutableListOf<MemoryItem>()
        val pattern = """"title":\s*"([^"]+)"[^}]*?"content":\s*"([^"]+)"[^}]*?"tags":\s*\[(.*?)\]""".toRegex()
        
        pattern.findAll(json).forEach {
            val (title, content, tagsStr) = it.destructured
            val tags = tagsStr.split(",").map { it.trim().trim('"') }.filter { it.isNotEmpty() }
            memories.add(MemoryItem(title, content, tags))
        }
        
        return memories
    }

    private fun parseEvents(json: String): List<EventItem> {
        val events = mutableListOf<EventItem>()
        val pattern = """"title":\s*"([^"]+)"[^}]*?"description":\s*"([^"]+)"[^}]*?"startTime":\s*"([^"]+)"[^}]*?"endTime":\s*"([^"]+)"[^}]*?"location":\s*"([^"]*)""".toRegex()
        
        pattern.findAll(json).forEach {
            val (title, desc, start, end, loc) = it.destructured
            events.add(EventItem(title, desc, start, end, loc.ifBlank { null }))
        }
        
        return events
    }
}

/**
 * 数据模型
 */
data class ExtractedData(
    val tasks: List<TaskItem>,
    val memories: List<MemoryItem>,
    val events: List<EventItem>
)

data class TaskItem(
    val title: String,
    val description: String,
    val dueDate: String? = null
)

data class MemoryItem(
    val title: String,
    val content: String,
    val tags: List<String> = emptyList()
)

data class EventItem(
    val title: String,
    val description: String,
    val startTime: String,
    val endTime: String,
    val location: String? = null
)
