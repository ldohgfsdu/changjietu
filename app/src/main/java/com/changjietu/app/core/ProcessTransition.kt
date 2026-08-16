package com.changjietu.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 处理完成的一次性导航契约（C2.3）：pending id + ack。
 *
 * 问题：不能用「slices 非空 且 非 processing」推断「一次新的处理完成」——slices 是
 * 持久业务状态，用户返回后旧 slices 仍在，若用它驱动导航会把用户重新弹回 preview。
 *
 * 本契约把「处理完成」建模为一次性事件：
 * - 仅 [markCompleted]（初始 processing 成功结束时）产生新的单调递增 pending id；
 * - UI 观察到非 null 后导航一次，再 [ack] 清空；ack 仅当 id 匹配当前 pending 才生效；
 * - 手动切片编辑（合并/删除/拖分割线）不产生 pending → 不会误导航。
 *
 * 与 C1 [ShareDelivery] 的 pending/ack 思路一致，但保持最小、独立，不泛化为全局事件系统。
 * 纯 JVM 可测（[MutableStateFlow] 无需 Android 运行时）。
 */
class ProcessTransition {
    private val _pending = MutableStateFlow<Long?>(null)

    /** 当前待处理的处理完成 id；null 表示无可消费的一次性导航事件。 */
    val pending: StateFlow<Long?> = _pending.asStateFlow()

    private var seq = 0L

    /** 标记一次 processing 成功完成，返回新分配（单调递增）的 pending id。 */
    fun markCompleted(): Long {
        seq++
        _pending.value = seq
        return seq
    }

    /**
     * 消费一次性导航事件。仅当 [id] 等于当前 pending 时清空并返回 true；
     * 否则（id 过期 / 已清空 / 不匹配）返回 false 且不改状态——旧 ack 不会清除新 pending。
     */
    fun ack(id: Long): Boolean =
        if (_pending.value == id) {
            _pending.value = null
            true
        } else {
            false
        }
}