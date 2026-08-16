package com.changjietu.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证外部分享一次性交付（pending/consumed/ack）与 recreation 语义。
 * Android Activity 交付接线本身为运行态，但决定其正确性的状态机在此自动化覆盖。
 */
class ShareDeliveryTest {

    @Test
    fun consume_setsPending_andMarksConsumed() {
        val d = ShareDelivery()
        val id = d.consume()
        assertFalse(id == 0L)
        assertEquals(id, d.pendingId)
        assertTrue(d.consumed)
    }

    @Test
    fun ack_matchingId_clearsPending() {
        val d = ShareDelivery()
        val id = d.consume()
        d.ack(id)
        assertNull(d.pendingId)
        assertTrue(d.consumed) // 已 ack 的实例仍标记为已消费，配置重建不再重复消费
    }

    @Test
    fun ack_nonMatchingId_doesNotClearPending() {
        val d = ShareDelivery()
        val id = d.consume()
        d.ack(id + 99)
        assertEquals(id, d.pendingId)
    }

    @Test
    fun consume_twice_pendingBecomesLatest() {
        val d = ShareDelivery()
        val first = d.consume()
        val second = d.consume()
        assertTrue(second > first)
        assertEquals(second, d.pendingId)
        // ack 旧 id 不清空最新 pending
        d.ack(first)
        assertEquals(second, d.pendingId)
        d.ack(second)
        assertNull(d.pendingId)
    }

    @Test
    fun newDelivery_stillCreatesRequest_afterAck() {
        // 新 onNewIntent 在 ack 后仍产生新请求。
        val d = ShareDelivery()
        val a = d.consume()
        d.ack(a)
        assertNull(d.pendingId)
        val b = d.consume()
        assertTrue(b > a)
        assertEquals(b, d.pendingId)
    }

    @Test
    fun processDeathRecreation_freshInstance_reconsumes() {
        // 进程死亡 → 新建实例（consumed=false）→ 可重新消费原始 Intent（cold-start 恢复）。
        val fresh = ShareDelivery()
        assertFalse(fresh.consumed)
        assertNull(fresh.pendingId)
        val id = fresh.consume()
        assertTrue(fresh.consumed)
        assertEquals(id, fresh.pendingId)
    }

    @Test
    fun configRecreation_sameInstance_doesNotReconsume() {
        // 配置重建 → 同一实例保留：consumed 仍为 true，pending 已 ack。
        val d = ShareDelivery()
        val id = d.consume()
        d.ack(id)
        // 重建后：不再产生新 pending（不重复导航），consumed 保持 true（不重开 URI）。
        assertNull(d.pendingId)
        assertTrue(d.consumed)
    }
}
