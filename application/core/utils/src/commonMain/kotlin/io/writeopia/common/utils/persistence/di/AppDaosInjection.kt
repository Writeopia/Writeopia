package io.writeopia.common.utils.persistence.di

import io.writeopia.common.utils.persistence.daos.FolderCommonDao
import io.writeopia.common.utils.persistence.daos.NotesConfigurationCommonDao
import io.writeopia.common.utils.persistence.daos.TokenCommonDao
import io.writeopia.common.utils.persistence.daos.UserCommonDao

interface AppDaosInjection {
    fun provideConfigurationDao(): NotesConfigurationCommonDao

    fun provideFolderDao(): FolderCommonDao

    fun provideUserDao(): UserCommonDao

    fun provideTokenDao(): TokenCommonDao
}
