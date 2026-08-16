package com.changjietu.app.core

/**
 * 外部分享交付的一次性状态机：consume → pending → ack。
 * 纯逻辑、无 Android 依赖，可在 JVM 单测验证 recreation / consume 语义。
 *
 * 设计目标（替代单纯单调递增 id）：
 * - **pendingId** 在 UI / collector 尚未建立时仍保留在 ViewModel，cold-start 分享不丢失。
 * - **ack(id)** 仅在 id 与当前 pending 匹配时清空，避免误清新请求、避免重复导航。
 * - **consumed** 为「本实例是否已消费一次分享」的去重护栏：
 *   进程死亡后新建实例为 false，可重新消费原始 Intent（cold-start 恢复）；
 *   同一 Activity 实例配置重建时实例保留、consumed 为 true，不再重复消费/重开 URI。
 * - 新 onNewIntent 调用 [consume] 产生新 id → 必然触发新导航。
 *
 * 不引入全局事件总线。
 */
class ShareDelivery {

    private var idCounter = 0L

    /** 当前待导航的分享请求 id；null 表示无待处理请求（已 ack）。 */
    var pendingId: Long? = null
        private set

    /** 本实例是否已消费过一次分享（配置重建去重护栏）。 */
    var consumed: Boolean = false
        private set

    /** 记录一个新分享请求，返回其单调递增 id；pending 更新为最新 id。 */
    fun consume(): Long {
        consumed = true
        idCounter++
        pendingId = idCounter
        return idCounter
    }

    /** 确认指定请求已处理（导航完成）；仅当与当前 pending 匹配才清空。 */
    fun ack(id: Long) {
        if (pendingId == id) pendingId = null
    }
}
