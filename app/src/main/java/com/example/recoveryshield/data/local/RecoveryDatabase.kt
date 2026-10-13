package com.example.recoveryshield.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProtectionStateEntity::class,
        DisableRequestEntity::class,
        AuditLogEntity::class,
        RewardLedgerEntity::class,
        FalsePositiveEntity::class,
        UserStatsEntity::class,
        UserProtectionPlanEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class RecoveryDatabase : RoomDatabase() {
    abstract fun protectionDao(): ProtectionDao
    abstract fun disableRequestDao(): DisableRequestDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun rewardLedgerDao(): RewardLedgerDao
    abstract fun falsePositiveDao(): FalsePositiveDao
    abstract fun userStatsDao(): UserStatsDao
    abstract fun userProtectionPlanDao(): UserProtectionPlanDao

    companion object {
        @Volatile
        private var INSTANCE: RecoveryDatabase? = null

        fun getInstance(context: Context): RecoveryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RecoveryDatabase::class.java,
                    "recovery_shield.db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
        }
    }
}
