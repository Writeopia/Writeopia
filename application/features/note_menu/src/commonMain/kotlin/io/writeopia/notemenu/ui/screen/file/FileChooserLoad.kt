package io.writeopia.notemenu.ui.screen.file

import io.writeopia.sdk.models.files.ExternalFile

expect fun fileChooserLoad(title: String = "Choose file"): List<ExternalFile>
