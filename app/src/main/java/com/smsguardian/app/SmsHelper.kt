package com.smsguardian.app

import android.content.ContentResolver
import android.net.Uri

/** 短信数据模型 */
data class SmsItem(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int
)

/** 通过 ContentResolver 读取系统短信收件箱。调用方需持有 READ_SMS 权限。 */
object SmsHelper {

    private val INBOX_URI: Uri = Uri.parse("content://sms/inbox")
    private val PROJECTION = arrayOf("_id", "address", "body", "date", "type")

    /** 查询最新一条收件箱短信 */
    fun queryLatestInbox(cr: ContentResolver): SmsItem? {
        val cursor = cr.query(INBOX_URI, PROJECTION, null, null, "date DESC") ?: return null
        return cursor.use { c ->
            if (c.moveToFirst()) fromCursor(c) else null
        }
    }

    /** 查询收件箱短信列表（最多 limit 条） */
    fun queryInbox(cr: ContentResolver, limit: Int = 300): List<SmsItem> {
        val cursor = cr.query(INBOX_URI, PROJECTION, null, null, "date DESC") ?: return emptyList()
        return cursor.use { c ->
            val list = ArrayList<SmsItem>(limit.coerceAtMost(50))
            while (c.moveToNext() && list.size < limit) {
                list.add(fromCursor(c))
            }
            list
        }
    }

    private fun fromCursor(c: android.database.Cursor): SmsItem {
        fun str(name: String): String {
            val idx = c.getColumnIndex(name)
            return if (idx >= 0) (c.getString(idx) ?: "") else ""
        }
        fun lng(name: String): Long {
            val idx = c.getColumnIndex(name)
            return if (idx >= 0) c.getLong(idx) else -1L
        }
        fun int(name: String): Int {
            val idx = c.getColumnIndex(name)
            return if (idx >= 0) c.getInt(idx) else -1
        }
        return SmsItem(
            id = lng("_id"),
            address = str("address"),
            body = str("body"),
            date = lng("date"),
            type = int("type")
        )
    }
}
