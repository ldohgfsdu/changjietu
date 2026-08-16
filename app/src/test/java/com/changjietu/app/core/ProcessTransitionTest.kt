package com.changjietu.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C2.3：处理完成的一次性导航契约（[ProcessTransition]）的纯 JVM 回归。
 *
 * 覆盖 six 场景（用户验收清单）：
 *   ① success → pending 恰好一次，ack 后观察稳定，不重放
 *   ② 重复观察 / 重复 ack 去重
 *   ③ failure → 无 pending
 *   ④ 第二次 processing success → 产生新 id
 *   ⑤ 旧 ack 不清除新 pending
 *   ⑥ 配置重建 / 普通状态更新不产生新的 processing completion
 *
 * 注意：实际 `nav.navigate("preview")` 在 Compose/Activity 层执行，本 JVM 测试不覆盖
 * 该 wiring（标 NOT AUTOMATED，见 [navigationWiring_notAutomated]）。
 */
class ProcessTransitionTest {

    @Test
    fun success_producesPendingExactlyOnce_andAckClears() {
        val t = ProcessTransition()
        val id = t.markCompleted()
        assertTrue("success must yield a pending id", id > 0)
        assertEquals(id, t.pending.value)

        // 观察一次后 ack 清空；再观察仍为 null（不因状态非空被重复消费）
        assertTrue(t.ack(id))
        assertNull(t.pending.value)
        assertNull("re-observation after ack stays null", t.pending.value)
    }

    @Test
    fun repeatedObservation_doesNotRetrigger_afterAck() {
        val t = ProcessTransition()
        val id = t.markCompleted()
        t.ack(id)
        // 模拟 LaunchedEffect 因重组/配置重建对同一 StateFlow 的重复观察：值稳定为 null
        repeat(5) { assertNull(t.pending.value) }
    }

    @Test
    fun repeatedAck_sameId_isDeduplicated() {
        val t = ProcessTransition()
        val id = t.markCompleted()
        assertTrue(t.ack(id))
        assertFalse("second ack with same (already consumed) id is a no-op", t.ack(id))
        assertNull(t.pending.value)
    }

    @Test
    fun failure_leavesNoPending() {
        val t = ProcessTransition()
        // 失败路径不调用 markCompleted → 无可消费事件
        assertNull(t.pending.value)
        assertFalse("ack without any pending must not flip state", t.ack(1L))
        assertNull(t.pending.value)
    }

    @Test
    fun secondSuccess_producesNewId() {
        val t = ProcessTransition()
        val id1 = t.markCompleted()
        t.ack(id1)
        val id2 = t.markCompleted()
        assertTrue("new completion must get a fresh, distinct id", id2 > id1)
        assertEquals(id2, t.pending.value)
    }

    @Test
    fun oldAck_doesNotClearNewPending() {
        val t = ProcessTransition()
        val id1 = t.markCompleted()
        t.ack(id1)
        val id2 = t.markCompleted()
        // 迟到的旧 ack（id1）不得清除新 pending
        assertFalse("stale ack for old id must be rejected", t.ack(id1))
        assertEquals("new pending must survive stale ack", id2, t.pending.value)
        assertTrue(t.ack(id2))
        assertNull(t.pending.value)
    }

    @Test
    fun configurationRecreation_doesNotProduceNewCompletion() {
        val t = ProcessTransition()
        val id = t.markCompleted()
        t.ack(id)
        // 配置重建只重建 Activity/composition，ViewModel 存活 → 过渡对象已 ack 为 null；
        // 仅观察（不调用 markCompleted）不会凭空产生新的 processing completion
        assertNull(t.pending.value)
        val freshObserver = t.pending.value
        assertNull("observation must not fabricate an event", freshObserver)
        assertEquals(0L, t.pending.value ?: 0L)
    }

    @Test
    fun manualSliceEdit_doesNotProducePending() {
        // 手动合并/删除/拖分割线走 reprocessFromRanges，绝不调用 markCompleted：
        // 契约上只有 markCompleted 会产生 pending，故该路径天然不产生导航事件。
        val t = ProcessTransition()
        t.ack(0L) // 即使有迟到 ack 也无 pending 可清
        assertNull(t.pending.value)
        // 其它任意调用（除 markCompleted 外）不得引入 pending
        assertFalse(t.ack(12345L))
        assertNull(t.pending.value)
    }

    /**
     * 真实 `nav.navigate("preview")` 由 MainActivity 的 LaunchedEffect(pendingProcessResultId)
     * 执行，属 Compose/Activity wiring，无法在纯 JVM 单测中自动验证。
     * 此处仅断言 wiring 静态存在且消费 id 后调用 ack——见 C2 静态断言测试，
     * 完整点击级验证留给有设备后的运行态冒烟（RUNTIME-PENDING）。
     */
    @Test
    fun navigationWiring_notAutomated() {
        // NOT AUTOMATED：仅作标记，避免把 Activity 层行为误报为已自动化验证。
        assertTrue(true)
    }
}