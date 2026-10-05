package cl.contasimp.tracker

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class QueueDb(context: Context): SQLiteOpenHelper(context, "tracker_queue.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE queue(id INTEGER PRIMARY KEY AUTOINCREMENT,payload TEXT NOT NULL,created_at INTEGER NOT NULL)") }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun add(payload: String) { writableDatabase.execSQL("INSERT INTO queue(payload,created_at) VALUES(?,?)", arrayOf(payload, System.currentTimeMillis())) }
    fun first(limit: Int = 50): List<Pair<Long,String>> { val out= mutableListOf<Pair<Long,String>>(); readableDatabase.rawQuery("SELECT id,payload FROM queue ORDER BY id LIMIT ?", arrayOf(limit.toString())).use{c->while(c.moveToNext())out.add(c.getLong(0) to c.getString(1))}; return out }
    fun delete(id: Long) { writableDatabase.execSQL("DELETE FROM queue WHERE id=?", arrayOf(id)) }
}
