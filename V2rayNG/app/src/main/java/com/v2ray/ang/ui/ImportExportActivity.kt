package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityImportExportBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImportExportActivity : BaseActivity() {
    private val binding by lazy {
        ActivityImportExportBinding.inflate(layoutInflater)
    }

    private val selectFileForImport = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val tempFile = File(cacheDir, "import_temp.json")
                inputStream?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                handleImportFile(tempFile)
            } catch (e: Exception) {
                toast(R.string.import_export_import_failed)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Export buttons
        binding.btnExportAll.setOnClickListener {
            exportData(ExportType.ALL)
        }

        binding.btnExportSubscriptions.setOnClickListener {
            exportData(ExportType.SUBSCRIPTIONS)
        }

        binding.btnExportServers.setOnClickListener {
            exportData(ExportType.SERVERS)
        }

        binding.btnExportSettings.setOnClickListener {
            exportData(ExportType.SETTINGS)
        }

        // Import buttons
        binding.btnImportAll.setOnClickListener {
            showImportDialog(ImportType.ALL)
        }

        binding.btnImportSubscriptions.setOnClickListener {
            showImportDialog(ImportType.SUBSCRIPTIONS)
        }

        binding.btnImportServers.setOnClickListener {
            showImportDialog(ImportType.SERVERS)
        }

        binding.btnImportSettings.setOnClickListener {
            showImportDialog(ImportType.SETTINGS)
        }
    }

    private fun exportData(type: ExportType) {
        binding.pbWaiting.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val filename = when (type) {
                    ExportType.ALL -> "v2rayNG_backup_all_$timestamp.json"
                    ExportType.SUBSCRIPTIONS -> "v2rayNG_backup_subs_$timestamp.json"
                    ExportType.SERVERS -> "v2rayNG_backup_servers_$timestamp.json"
                    ExportType.SETTINGS -> "v2rayNG_backup_settings_$timestamp.json"
                }

                val backupData = when (type) {
                    ExportType.ALL -> BackupManager.exportAllData()
                    ExportType.SUBSCRIPTIONS -> BackupManager.exportSubscriptions()
                    ExportType.SERVERS -> BackupManager.exportAllData().copy(
                        subscriptions = null,
                        settings = null
                    )
                    ExportType.SETTINGS -> BackupManager.exportAllData().copy(
                        subscriptions = null,
                        servers = null,
                        serverRaws = null,
                        serverAffiliations = null
                    )
                }

                val file = BackupManager.saveBackupToFile(this@ImportExportActivity, backupData, filename)

                withContext(Dispatchers.Main) {
                    binding.pbWaiting.visibility = View.GONE
                    if (file != null) {
                        showExportSuccessDialog(file)
                    } else {
                        toast(R.string.import_export_export_failed)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.pbWaiting.visibility = View.GONE
                    toast(R.string.import_export_export_failed)
                }
            }
        }
    }

    private fun showExportSuccessDialog(file: File) {
        AlertDialog.Builder(this)
            .setTitle(R.string.toast_success)
            .setMessage(getString(R.string.import_export_file_saved, file.absolutePath))
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.menu_item_share) { _, _ ->
                shareFile(file)
            }
            .show()
    }

    private fun shareFile(file: File) {
        try {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "application/json"
            intent.putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            ))
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, getString(R.string.title_configuration_share)))
        } catch (e: Exception) {
            toast("Failed to share file")
        }
    }

    private fun showImportDialog(type: ImportType) {
        if (type == ImportType.ALL) {
            AlertDialog.Builder(this)
                .setTitle(R.string.import_export_import_all)
                .setMessage(R.string.import_export_confirm_import_replace)
                .setPositiveButton(R.string.import_export_merge) { _, _ ->
                    selectImportFile(type, false)
                }
                .setNegativeButton(R.string.import_export_replace) { _, _ ->
                    selectImportFile(type, true)
                }
                .setNeutralButton(android.R.string.cancel, null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle(when (type) {
                    ImportType.SUBSCRIPTIONS -> R.string.import_export_import_subscriptions
                    ImportType.SERVERS -> R.string.import_export_import_servers
                    ImportType.SETTINGS -> R.string.import_export_import_settings
                    else -> R.string.import_export_import_all
                })
                .setMessage(R.string.import_export_confirm_import)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    selectImportFile(type, false)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun selectImportFile(type: ImportType, replace: Boolean) {
        currentImportType = type
        currentImportReplace = replace
        
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/json"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        selectFileForImport.launch(Intent.createChooser(intent, getString(R.string.import_export_select_file)))
    }

    private var currentImportType = ImportType.ALL
    private var currentImportReplace = false

    private fun handleImportFile(file: File) {
        binding.pbWaiting.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val backupData = BackupManager.loadBackupFromFile(file)
                if (backupData == null) {
                    withContext(Dispatchers.Main) {
                        binding.pbWaiting.visibility = View.GONE
                        toast(R.string.import_export_import_failed)
                    }
                    return@launch
                }

                val result = when (currentImportType) {
                    ImportType.ALL -> {
                        val (subs, servers) = BackupManager.importAllData(backupData, currentImportReplace)
                        val settings = BackupManager.importSettings(backupData.settings)
                        Triple(subs, servers, settings)
                    }
                    ImportType.SUBSCRIPTIONS -> {
                        val subs = BackupManager.importSubscriptions(backupData)
                        Triple(subs, 0, 0)
                    }
                    ImportType.SERVERS -> {
                        val servers = BackupManager.importServers(backupData)
                        Triple(0, servers, 0)
                    }
                    ImportType.SETTINGS -> {
                        val settings = BackupManager.importSettings(backupData.settings)
                        Triple(0, 0, settings)
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.pbWaiting.visibility = View.GONE
                    val message = getString(
                        R.string.import_export_import_success,
                        result.first,
                        result.second,
                        result.third
                    )
                    AlertDialog.Builder(this@ImportExportActivity)
                        .setTitle(R.string.toast_success)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            setResult(RESULT_OK)
                            finish()
                        }
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.pbWaiting.visibility = View.GONE
                    toast(R.string.import_export_import_failed)
                }
            } finally {
                file.delete()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    enum class ExportType {
        ALL, SUBSCRIPTIONS, SERVERS, SETTINGS
    }

    enum class ImportType {
        ALL, SUBSCRIPTIONS, SERVERS, SETTINGS
    }
}
