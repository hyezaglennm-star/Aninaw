package com.aninaw.data.lifesnapshot

class LifeSnapshotRepository(private val dao: LifeSnapshotDao) {

    suspend fun baseline(): LifeSnapshotEntity? = dao.getBaseline()
    suspend fun latestCurrent(): LifeSnapshotEntity? = dao.getCurrent()

    suspend fun saveBaseline(item: LifeSnapshotEntity) {
        dao.upsert(item.copy(id = 1, kind = "BASELINE"))
    }

    suspend fun saveCurrent(item: LifeSnapshotEntity) {
        dao.upsert(item.copy(id = 2, kind = "CURRENT"))
    }
}