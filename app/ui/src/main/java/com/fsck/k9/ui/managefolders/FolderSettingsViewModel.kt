package com.fsck.k9.ui.managefolders

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.fsck.k9.Account
import com.fsck.k9.Preferences
import com.fsck.k9.activity.FolderInfoHolder
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.helper.SingleLiveEvent
import com.fsck.k9.mailstore.Folder
import com.fsck.k9.mailstore.FolderDetails
import com.fsck.k9.mailstore.FolderRepository
import com.fsck.k9.mailstore.FolderRepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class FolderSettingsViewModel(
    private val preferences: Preferences,
    private val folderRepositoryManager: FolderRepositoryManager,
    private val messagingController: MessagingController
) : ViewModel() {
    private val actionLiveData = SingleLiveEvent<Action>()
    private var folderSettingsLiveData: LiveData<FolderSettingsResult>? = null

    private lateinit var folderAccount: Account
    private lateinit var folderServerId: String

    fun getFolderSettingsLiveData(accountUuid: String, folderId: Long): LiveData<FolderSettingsResult> {
        return folderSettingsLiveData ?: createFolderSettingsLiveData(accountUuid, folderId).also {
            folderSettingsLiveData = it
        }
    }

    private fun createFolderSettingsLiveData(
        accountUuid: String,
        folderId: Long
    ): LiveData<FolderSettingsResult> {
        return liveData(context = viewModelScope.coroutineContext) {
            folderAccount = loadAccount(accountUuid)

            val folderRepository = folderRepositoryManager.getFolderRepository(folderAccount)
            val folderDetails = folderRepository.loadFolderDetails(folderId)
            if (folderDetails == null) {
                Timber.w("Folder with ID $folderId not found")
                emit(FolderNotFound)
                return@liveData
            }

            folderServerId = folderDetails.folder.serverId

            val folderSettingsData = FolderSettingsData(
                folder = createFolderObject(folderAccount, folderDetails.folder),
                dataStore = FolderSettingsDataStore(folderRepository, folderDetails)
            )
            emit(folderSettingsData)
        }
    }

    private suspend fun loadAccount(accountUuid: String): Account {
        return withContext(Dispatchers.IO) {
            preferences.getAccount(accountUuid) ?: error("Missing account: $accountUuid")
        }
    }

    private suspend fun FolderRepository.loadFolderDetails(folderId: Long): FolderDetails? {
        return withContext(Dispatchers.IO) {
            getFolderDetails(folderId)
        }
    }

    private fun createFolderObject(account: Account, folder: Folder): Folder {
        val folderType = FolderInfoHolder.getFolderType(account, folder.serverId)
        return Folder(
            id = folder.id,
            serverId = folder.serverId,
            name = folder.name,
            type = folderType
        )
    }

    fun showClearFolderConfirmationDialog() {
        sendActionEvent(Action.ShowClearFolderConfirmationDialog)
    }

    fun onClearFolderConfirmation() {
        messagingController.clearFolder(folderAccount, folderServerId, null)
    }

    fun getActionEvents(): LiveData<Action> = actionLiveData

    private fun sendActionEvent(action: Action) {
        actionLiveData.value = action
    }
}

sealed class FolderSettingsResult
object FolderNotFound : FolderSettingsResult()
data class FolderSettingsData(val folder: Folder, val dataStore: FolderSettingsDataStore) : FolderSettingsResult()

sealed class Action {
    object ShowClearFolderConfirmationDialog : Action()
}
