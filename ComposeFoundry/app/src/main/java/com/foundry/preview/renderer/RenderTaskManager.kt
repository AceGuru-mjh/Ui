package com.foundry.preview.renderer

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 渲染进程内单例任务管理器。
 *
 * ## 用途
 * 解决 Service 无 Window 的问题：RendererService 通过 Binder 接收渲染请求后，
 * 提交任务到本管理器 → 启动 RenderCaptureActivity → latch.await(30s) 阻塞等待 →
 * Activity 渲染完成后调用 completeTask 释放 latch → Service 拿到文件路径返回主进程。
 *
 * ## 线程安全
 * - [ConcurrentHashMap] 支持多线程并发提交/完成
 * - [CountDownLatch] 确保 await 语义正确
 *
 * ## 超时策略
 * - 默认 30 秒超时，防止僵尸 Activity 永久阻塞 Service 线程
 * - 超时后返回 null，主进程显示"渲染超时"诊断信息
 */
object RenderTaskManager {

    private const val TAG = "RenderTaskManager"
    private const val DEFAULT_TIMEOUT_SEC = 30L

    data class RenderTask(
        val taskId: String,
        val payload: String,
        val sdkId: String,
        val latch: CountDownLatch = CountDownLatch(1)
    ) {
        /** 渲染结果：PNG 文件路径，完成前为 null。 */
        @Volatile var outputPath: String? = null
    }

    private val tasks = ConcurrentHashMap<String, RenderTask>()

    /**
     * 提交渲染任务，返回 taskId。
     * 调用方（RendererService）随后应启动 RenderCaptureActivity 并通过 Intent 传递 taskId。
     */
    fun submitTask(payload: String, sdkId: String): String {
        val taskId = "task_${System.nanoTime()}_${payload.hashCode()}"
        val task = RenderTask(taskId = taskId, payload = payload, sdkId = sdkId)
        tasks[taskId] = task
        Log.i(TAG, "Task submitted: $taskId (sdk=$sdkId, len=${payload.length})")
        return taskId
    }

    /**
     * 获取任务对象（RenderCaptureActivity 在 onCreate 时调用）。
     */
    fun getTask(taskId: String): RenderTask? = tasks[taskId]

    /**
     * RenderCaptureActivity 渲染完成时调用，设置 outputPath 并释放 latch。
     */
    fun completeTask(taskId: String, outputPath: String) {
        val task = tasks[taskId]
        if (task != null) {
            task.outputPath = outputPath
            task.latch.countDown()
            Log.i(TAG, "Task completed: $taskId → $outputPath")
        } else {
            Log.w(TAG, "completeTask: unknown taskId $taskId")
        }
    }

    /**
     * RenderCaptureActivity 渲染失败时调用。
     */
    fun failTask(taskId: String, error: String) {
        val task = tasks[taskId]
        if (task != null) {
            task.outputPath = null
            task.latch.countDown()
            Log.e(TAG, "Task failed: $taskId, error=$error")
        }
    }

    /**
     * 阻塞等待渲染任务完成。
     *
     * @param taskId       任务 ID
     * @param timeoutSec   超时秒数（默认 30 秒）
     * @return PNG 文件路径，超时或失败返回 null
     */
    fun awaitResult(taskId: String, timeoutSec: Long = DEFAULT_TIMEOUT_SEC): String? {
        val task = tasks[taskId]
        if (task == null) {
            Log.w(TAG, "awaitResult: unknown taskId $taskId")
            return null
        }
        val success = task.latch.await(timeoutSec, TimeUnit.SECONDS)
        val result = if (success) task.outputPath else null
        if (!success) {
            Log.w(TAG, "Task $taskId timed out after ${timeoutSec}s")
        }
        // 清理已完成/超时任务
        tasks.remove(taskId)
        return result
    }

    /** 清理所有未完成任务（Service onDestroy 时调用）。 */
    fun clearAll() {
        tasks.values.forEach { task ->
            task.outputPath = null
            task.latch.countDown()  // 释放所有阻塞线程
        }
        tasks.clear()
        Log.i(TAG, "All tasks cleared")
    }
}
