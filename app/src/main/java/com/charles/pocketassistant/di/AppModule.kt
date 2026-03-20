package com.charles.pocketassistant.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.WorkManager
import com.charles.pocketassistant.ai.parser.AiJsonParser
import com.charles.pocketassistant.data.datastore.SettingsStore
import com.charles.pocketassistant.data.db.PocketAssistantDatabase
import com.charles.pocketassistant.data.db.dao.AiResultDao
import com.charles.pocketassistant.data.db.dao.ChatMessageDao
import com.charles.pocketassistant.data.db.dao.ChatThreadDao
import com.charles.pocketassistant.data.db.dao.ItemDao
import com.charles.pocketassistant.data.db.dao.ReminderDao
import com.charles.pocketassistant.data.db.dao.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    private val migration1To2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_threads` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_messages` (
                    `id` TEXT NOT NULL,
                    `threadId` TEXT NOT NULL,
                    `role` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `actionsJson` TEXT NOT NULL,
                    `referencesJson` TEXT NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`threadId`) REFERENCES `chat_threads`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_threadId` ON `chat_messages` (`threadId`)")
        }
    }

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): PocketAssistantDatabase =
        Room.databaseBuilder(context, PocketAssistantDatabase::class.java, "pocket_assistant.db")
            .addMigrations(migration1To2)
            .build()

    @Provides fun provideItemDao(db: PocketAssistantDatabase): ItemDao = db.itemDao()
    @Provides fun provideAiDao(db: PocketAssistantDatabase): AiResultDao = db.aiResultDao()
    @Provides fun provideTaskDao(db: PocketAssistantDatabase): TaskDao = db.taskDao()
    @Provides fun provideReminderDao(db: PocketAssistantDatabase): ReminderDao = db.reminderDao()
    @Provides fun provideChatThreadDao(db: PocketAssistantDatabase): ChatThreadDao = db.chatThreadDao()
    @Provides fun provideChatMessageDao(db: PocketAssistantDatabase): ChatMessageDao = db.chatMessageDao()

    @Provides @Singleton fun provideSettingsStore(@ApplicationContext context: Context) = SettingsStore(context)
    @Provides @Singleton fun provideParser() = AiJsonParser()
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    @Provides @Singleton fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
