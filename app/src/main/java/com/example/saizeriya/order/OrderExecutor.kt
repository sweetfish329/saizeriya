package com.example.saizeriya.order

import com.example.saizeriya.data.model.OrderSession

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
        // 1. セッション作成
        val session = saizeriyaClient.createSession(qrUrl, peopleCount)

        // 2. カートにアイテム追加
        for (code in menuCodes) {
            saizeriyaClient.addItem(session.sessionId, code, count = 1)
        }

        // 3. 注文送信
        return saizeriyaClient.submitOrder(session.sessionId)
    }
}
