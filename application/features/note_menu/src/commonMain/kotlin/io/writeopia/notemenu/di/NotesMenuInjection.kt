package io.writeopia.notemenu.di

import androidx.compose.runtime.Composable
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.notemenu.viewmodel.ChooseNoteViewModel

interface NotesMenuInjection {

    @Composable
    fun provideChooseNoteViewModel(notesNavigation: NotesNavigation): ChooseNoteViewModel
}
