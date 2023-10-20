package io.opencola.relay.common.message.store

import io.opencola.application.TestApplication
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.relay.common.message.v2.store.ExposedMessageStore
import io.opencola.security.generateKeyPair
import io.opencola.security.initProvider
import io.opencola.storage.db.getPostgresDB
import io.opencola.storage.db.getSQLiteDB
import io.opencola.storage.filestore.ContentAddressedFileStore
import io.opencola.storage.filestore.FileSystemContentAddressedFileStore
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals


class ExposedMessageStoreTest {
    init {
        initProvider()
    }


    private fun newSQLiteDB(): Database {
        val dbDirectory = TestApplication.getTmpDirectory("-db")
        return getSQLiteDB(dbDirectory.resolve("test.db"))
    }

    private fun newPostgresDB(): Database {
        val db = getPostgresDB("localhost", "opencola", "opencola", "test")

        transaction(db) {
            SchemaUtils.drop(ExposedMessageStore.Messages())
        }

        return db
    }

    private fun newExposedDB(): Database {
        return newSQLiteDB()
    }

    private fun newFileStore(): FileSystemContentAddressedFileStore {
        val fileStoreDirectory = TestApplication.getTmpDirectory("-filestore")
        return FileSystemContentAddressedFileStore(fileStoreDirectory)
    }

    private fun newExposedMessageStore(
        maxStoredBytesPerConnection: Int = 1024 * 1024 * 50,
        exposedDB: Database = newExposedDB(),
        fileStore: ContentAddressedFileStore = newFileStore()
    ): ExposedMessageStore {
        return ExposedMessageStore(exposedDB, fileStore, maxStoredBytesPerConnection)
    }

    @Test
    fun testBasicAdd() {
        testBasicAdd(newExposedMessageStore())
    }

    @Test
    fun testAddMultipleMessages() {
        testAddMultipleMessages(newExposedMessageStore())
    }

    @Test
    fun testAddWithDuplicateMessageStorageKey() {
        testAddWithDuplicateMessageStorageKey(newExposedMessageStore())
    }

    @Test
    fun testRejectMessageWhenOverQuota() {
        testRejectMessageWhenOverQuota(newExposedMessageStore(34))
    }

    @Test
    fun testAddAddSameMessageDifferentFrom() {
        testAddAddSameMessageDifferentFrom(newExposedMessageStore())
    }

    @Test
    fun testNoMessageStorageKey() {
        testNoMessageStorageKey(newExposedMessageStore())
    }

    @Test
    fun testSafeDeleteFromFileStore() {
        val fileStore = newFileStore()
        val messageStore = newExposedMessageStore(fileStore = fileStore)
        val message = "message".toSignedBytes()
        val messageStorageKey = MessageStorageKey.of("key")
        val fromPublicKey = generateKeyPair().public
        val recipient0 = generateKeyPair().public.toRecipient()
        val recipient1 = generateKeyPair().public.toRecipient()

        messageStore.addMessage(fromPublicKey, recipient0, messageStorageKey, message)
        messageStore.addMessage(fromPublicKey, recipient1, messageStorageKey, message)

        assertEquals(1, fileStore.enumerateFileIds().count())
        messageStore.getMessages(recipient0.publicKey).forEach { messageStore.removeMessage(it) }
        assertEquals(1, fileStore.enumerateFileIds().count())
        messageStore.getMessages(recipient1.publicKey).forEach { messageStore.removeMessage(it) }
        assertEquals(0, fileStore.enumerateFileIds().count())
    }

    @Test
    fun testUnsafeDeleteFromFileStore() {
        val fileStore = newFileStore()
        val messageStore = newExposedMessageStore(fileStore = fileStore)
        val message = "message".toSignedBytes()
        val messageStorageKey = MessageStorageKey.of("key")
        val fromPublicKey = generateKeyPair().public
        val recipient0 = generateKeyPair().public.toRecipient()

        messageStore.addMessage(fromPublicKey, recipient0, messageStorageKey, message)

        assertEquals(1, fileStore.enumerateFileIds().count())
        assertEquals(1, messageStore.getMessages(recipient0.publicKey).count())
        fileStore.enumerateFileIds().forEach { fileStore.delete(it) }
        assertEquals(0, fileStore.enumerateFileIds().count())
        assertEquals(0, messageStore.getMessages(recipient0.publicKey).count())

    }
}