package com.topjohnwu.magisk.ui.install

import android.app.Activity
import android.net.Uri
import androidx.databinding.Bindable
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.BR
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.arch.BaseViewModel
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.download.Action
import com.topjohnwu.magisk.core.download.DownloadService
import com.topjohnwu.magisk.core.download.Subject
import com.topjohnwu.magisk.data.repository.StringRepository
import com.topjohnwu.magisk.events.MagiskInstallFileEvent
import com.topjohnwu.magisk.events.dialog.SecondSlotWarningDialog
import com.topjohnwu.magisk.utils.set
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.launch
import org.koin.core.get
import kotlin.math.roundToInt

class InstallViewModel(
    stringRepo: StringRepository
) : BaseViewModel(State.LOADED) {

    val isRooted = Shell.rootAccess()
    val skipOptions = Info.ramdisk && !Info.isFDE && Info.isSAR

    @get:Bindable
    var step = if (skipOptions) 1 else 0
        set(value) = set(value, field, { field = it }, BR.step)

    var _method = -1

    @get:Bindable
    var method
        get() = _method
        set(value) = set(value, _method, { _method = it }, BR.method) {
            when (it) {
                R.id.method_patch -> {
                    MagiskInstallFileEvent { code, intent ->
                        if (code == Activity.RESULT_OK)
                            data = intent?.data
                    }.publish()
                }
                R.id.method_inactive_slot -> {
                    SecondSlotWarningDialog().publish()
                }
            }
        }

    @get:Bindable
    var progress = 0
        set(value) = set(value, field, { field = it }, BR.progress)

    @get:Bindable
    var data: Uri? = null
        set(value) = set(value, field, { field = it }, BR.data)

    @get:Bindable
    var notes = ""
        set(value) = set(value, field, { field = it }, BR.notes)

    init {
        viewModelScope.launch {
            notes = stringRepo.getString(Info.remote.magisk.note)
        }
    }

    fun onProgressUpdate(progress: Float, subject: Subject) {
        if (subject !is Subject.Magisk) {
            return
        }
        this.progress = progress.times(100).roundToInt()
        if (this.progress >= 100) {
            state = State.LOADED
        }
    }

    fun step(nextStep: Int) {
        step = nextStep
    }

    fun install() {
        DownloadService.start(get(), Subject.Magisk(resolveAction()))
        state = State.LOADING
    }

    // ---

    private fun resolveAction() = when (method) {
        R.id.method_download -> Action.Download
        R.id.method_patch -> Action.Patch(data!!)
        R.id.method_direct -> Action.Flash.Primary
        R.id.method_inactive_slot -> Action.Flash.Secondary
        else -> error("Unknown value")
    }
}
