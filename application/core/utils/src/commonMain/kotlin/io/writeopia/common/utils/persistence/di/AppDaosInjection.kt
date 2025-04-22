package io.writeopia.common.utils.persistence.di

import io.writeopia.common.utils.persistence.daos.FolderCommonDao
import io.writeopia.common.utils.persistence.daos.NotesConfigurationCommonDao

interface AppDaosInjection {
    fun provideConfigurationDao(): NotesConfigurationCommonDao

    fun provideFolderDao(): FolderCommonDao
}
