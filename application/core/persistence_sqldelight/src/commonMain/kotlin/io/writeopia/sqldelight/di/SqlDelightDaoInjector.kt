package io.writeopia.sqldelight.di

import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.persistence.core.repository.InMemoryDocumentRepository
import io.writeopia.sdk.persistence.sqldelight.dao.DocumentSqlDao
import io.writeopia.sdk.persistence.sqldelight.dao.sql.SqlDelightDocumentRepository
import io.writeopia.sql.WriteopiaDb

class SqlDelightDaoInjector(
    private val database: WriteopiaDb?,
) : RepositoryInjector {

    private var documentSqlDao: DocumentSqlDao? = null

    private var sqlDelightDocumentRepository: SqlDelightDocumentRepository? = null

    private val inMemoryDocumentRepository = InMemoryDocumentRepository()

    private fun provideDocumentSqlDao(): DocumentSqlDao? =
        database?.run {
            documentSqlDao ?: kotlin.run {
                documentSqlDao = DocumentSqlDao(
                    documentEntityQueries,
                    storyStepEntityQueries,
                )

                documentSqlDao
            }
        }

    override fun provideDocumentRepository(): DocumentRepository =
        sqlDelightDocumentRepository ?: kotlin.run {
            sqlDelightDocumentRepository =
                provideDocumentSqlDao()?.let(::SqlDelightDocumentRepository)

            sqlDelightDocumentRepository ?: inMemoryDocumentRepository
        }


    companion object {
        private var instance: SqlDelightDaoInjector? = null

        fun singleton(): SqlDelightDaoInjector =
            instance ?: SqlDelightDaoInjector(WriteopiaDbInjector.singleton()?.database).also {
                instance = it
            }
    }
}
