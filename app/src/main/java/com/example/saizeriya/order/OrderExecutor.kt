package com.example.saizeriya.order

import com.example.saizeriya.data.model.OrderSession
import com.example.saizeriya.util.AppLogger

/**
 * SaizeriyaClientを使って実際の注文を実行する。
 */
class OrderExecutor(
    private val saizeriyaClient: SaizeriyaClient
) {
    /**
     * メニューコードリストで注文を実行する。
     *
     * @param qrUrl QRコードURL
     * @param peopleCount 人数
     * @param menuCodes 注文するメニューコードリスト
     * @return 注文完了後のセッション状態
     */
    suspend fun execute(
        qrUrl: String,
        peopleCount: Int,
        menuCodes: List<String>
    ): OrderSession {
        AppLogger.i("Executing order for $peopleCount people with codes: $menuCodes")
        // 1. セッション作成
        AppLogger.i("Creating session for QR: $qrUrl")
        val session = saizeriyaClient.createSession(qrUrl, peopleCount)
        AppLogger.i("Session created. SessionID: ${session.sessionId}")

        // 2. カートにアイテム追加
        for (code in menuCodes) {
            AppLogger.i("Adding item to cart: $code")
            saizeriyaClient.addItem(session.sessionId, code, count = 1)
        }

        // 3. 注文送信
        AppLogger.i("Submitting order for session: ${session.sessionId}")
        val result = saizeriyaClient.submitOrder(session.sessionId)
        AppLogger.i("Order submitted successfully. Final session state: $result")
        return result
    }
}
