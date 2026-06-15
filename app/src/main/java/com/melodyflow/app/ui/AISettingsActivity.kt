package com.melodyflow.app.ui

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.melodyflow.app.MelodyFlowApp
import com.melodyflow.app.R
import com.melodyflow.app.databinding.ActivityAiSettingsBinding
import com.melodyflow.app.model.AIConfig
import com.melodyflow.app.model.AIProvider
import com.melodyflow.app.service.AIService
import kotlinx.coroutines.launch

class AISettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiSettingsBinding
    private val repository by lazy { (application as MelodyFlowApp).repository }
    private var currentConfig: AIConfig? = null
    private var selectedProvider: AIProvider = AIProvider.CUSTOM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupProviderSpinner()
        setupListeners()
        loadConfig()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupProviderSpinner() {
        val providers = AIProvider.values().map { it.displayName }
        // 使用自定义布局来设置文本颜色
        val adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            providers
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as android.widget.TextView).setTextColor(getColor(R.color.text_primary))
                view.textSize = 14f
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as android.widget.TextView).setTextColor(getColor(R.color.text_primary))
                view.setBackgroundColor(getColor(R.color.surface))
                view.setPadding(24, 24, 24, 24)
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerProvider.adapter = adapter

        binding.spinnerProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedProvider = AIProvider.values()[position]
                updateFieldsForProvider(selectedProvider)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateFieldsForProvider(provider: AIProvider) {
        when (provider) {
            AIProvider.ASTRBOT, AIProvider.CUSTOM -> {
                // 自定义配置，保留用户输入
            }
            else -> {
                // 预设提供商，自动填充默认值
                if (binding.editApiUrl.text.toString().isEmpty()) {
                    binding.editApiUrl.setText(provider.defaultUrl)
                }
                if (binding.editModel.text.toString().isEmpty()) {
                    binding.editModel.setText(provider.defaultModel)
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }

        binding.btnSave.setOnClickListener {
            saveConfig()
        }

        binding.btnGetModels.setOnClickListener {
            fetchModels()
        }
    }

    private fun fetchModels() {
        val apiUrl = binding.editApiUrl.text.toString().trim()
        val apiKey = binding.editApiKey.text.toString().trim()

        if (apiUrl.isEmpty()) {
            binding.editApiUrl.error = "请先输入API地址"
            return
        }

        if (apiKey.isEmpty()) {
            binding.editApiKey.error = "请先输入API Key"
            return
        }

        // 创建一个临时配置用于获取模型
        val tempConfig = AIConfig(
            provider = selectedProvider,
            apiUrl = apiUrl,
            apiKey = apiKey,
            model = "",
            isEnabled = true
        )

        lifecycleScope.launch {
            binding.btnGetModels.isEnabled = false
            binding.btnGetModels.text = "获取中..."

            try {
                val service = AIService(tempConfig)
                val (models, error) = service.getAvailableModels()

                if (models.isNotEmpty()) {
                    showModelSelectionDialog(models)
                } else {
                    // 即使失败也显示预设模型
                    showModelSelectionDialog(emptyList())
                    if (error != null) {
                        Toast.makeText(this@AISettingsActivity, "$error，显示预设模型", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                // 出现异常也显示预设模型
                showModelSelectionDialog(emptyList())
                Toast.makeText(this@AISettingsActivity, "获取失败: ${e.message}，显示预设模型", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnGetModels.isEnabled = true
                binding.btnGetModels.text = "获取模型"
            }
        }
    }

    private fun showModelSelectionDialog(models: List<String>) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("选择模型")

        // 如果获取到的模型列表为空，显示预设模型
        val modelList = if (models.isEmpty()) {
            getPresetModels()
        } else {
            models
        }

        // 过滤出常见的聊天模型
        val chatModels = modelList.filter { model ->
            model.contains("gpt", ignoreCase = true) ||
            model.contains("claude", ignoreCase = true) ||
            model.contains("qwen", ignoreCase = true) ||
            model.contains("llama", ignoreCase = true) ||
            model.contains("chat", ignoreCase = true) ||
            model.contains("gemini", ignoreCase = true) ||
            model.contains("spark", ignoreCase = true) ||
            model.contains("turbo", ignoreCase = true) ||
            model.contains("max", ignoreCase = true)
        }.takeIf { it.isNotEmpty() } ?: modelList

        val modelArray = chatModels.toTypedArray()
        
        builder.setItems(modelArray) { _, which ->
            val selectedModel = modelArray[which]
            binding.editModel.setText(selectedModel)
            Toast.makeText(this, "已选择: $selectedModel", Toast.LENGTH_SHORT).show()
        }

        builder.setNegativeButton("取消", null)
        if (models.isEmpty()) {
            builder.setMessage("无法获取模型列表，显示常用预设模型")
        }
        builder.show()
    }

    /**
     * 获取预设的常用模型列表
     */
    private fun getPresetModels(): List<String> {
        return listOf(
            // One API 自定义模型格式
            "openai/xopqwen36v35b",
            "openai/gpt-4",
            "openai/gpt-4-turbo",
            "openai/gpt-3.5-turbo",
            // OpenAI
            "gpt-4",
            "gpt-4-turbo",
            "gpt-3.5-turbo",
            "gpt-4o",
            "gpt-4o-mini",
            // 讯飞星火 / 通义千问
            "qwen-turbo",
            "qwen-max",
            "qwen-plus",
            "qwen",
            "qwen-7b",
            "qwen-14b",
            "qwen-72b",
            // Anthropic
            "claude-3-opus",
            "claude-3-sonnet",
            "claude-3-haiku",
            "claude-3-5-sonnet",
            // Google
            "gemini-pro",
            "gemini-1.5-pro",
            "gemini-1.5-flash",
            // 其他常见模型
            "llama2-70b",
            "llama2-13b",
            "llama3-8b",
            "llama3-70b",
            "chatglm",
            "chatglm2",
            "chatglm3",
            "baichuan",
            "baichuan2"
        )
    }

    private fun loadConfig() {
        lifecycleScope.launch {
            repository.getAIConfig()?.let { config ->
                currentConfig = config
                binding.switchEnableAI.isChecked = config.isEnabled
                binding.editApiUrl.setText(config.apiUrl)
                binding.editApiKey.setText(config.apiKey)
                binding.editModel.setText(config.model)

                // 设置Spinner选中项
                val providerPosition = AIProvider.values().indexOf(config.provider)
                if (providerPosition >= 0) {
                    binding.spinnerProvider.setSelection(providerPosition)
                }
            }
        }
    }

    private fun testConnection() {
        val config = getConfigFromFields() ?: return

        lifecycleScope.launch {
            binding.btnTestConnection.isEnabled = false
            binding.btnTestConnection.text = "测试中..."

            try {
                val service = AIService(config)
                val (success, message) = service.testConnection()

                if (success) {
                    Toast.makeText(this@AISettingsActivity, message, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@AISettingsActivity, message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AISettingsActivity, "连接错误: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnTestConnection.isEnabled = true
                binding.btnTestConnection.text = "测试连接"
            }
        }
    }

    private fun saveConfig() {
        val config = getConfigFromFields() ?: return

        lifecycleScope.launch {
            repository.saveAIConfig(config)
            Toast.makeText(this@AISettingsActivity, "设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun getConfigFromFields(): AIConfig? {
        val apiUrl = binding.editApiUrl.text.toString().trim()
        val apiKey = binding.editApiKey.text.toString().trim()
        val model = binding.editModel.text.toString().trim()

        if (apiUrl.isEmpty()) {
            binding.editApiUrl.error = "请输入API地址"
            return null
        }

        if (apiKey.isEmpty()) {
            binding.editApiKey.error = "请输入API Key"
            return null
        }

        if (model.isEmpty()) {
            binding.editModel.error = "请输入模型名称"
            return null
        }

        return AIConfig(
            provider = selectedProvider,
            apiUrl = apiUrl,
            apiKey = apiKey,
            model = model,
            isEnabled = binding.switchEnableAI.isChecked
        )
    }
}
