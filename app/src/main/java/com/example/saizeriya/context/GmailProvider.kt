package com.example.saizeriya.context

import com.example.saizeriya.data.model.PurchaseData

/**
 * Gmail APIから決済通知メールを解析し、購買行動データを抽出するProvider。
 *
 * 主な対象メール:
 * - クレジットカード利用通知
 * - 電子マネー決済通知
 * - フードデリバリーの注文確認
 *
 * 注意: Gmail API の OAuth2 認証が必要。
 * Google Cloud Console でプロジェクトを作成し、Gmail API を有効化すること。
 */
open class GmailProvider {

    /**
     * 直近24時間の決済通知メールから購買行動データを抽出する。
     *
     * 実装手順:
     * 1. Gmail API でメールを検索（クエリ: "決済" OR "利用" OR "注文"）
     * 2. メール本文からレストラン名・金額を正規表現で抽出
     * 3. PurchaseData に変換
     *
     * @return PurchaseData（食事内容・出費傾向）
     */
    open suspend fun collect(): PurchaseData {
        // TODO: Gmail API 統合
        // MVP では空データを返し、Phase 7 で本実装する
        return PurchaseData(
            recentMeals = emptyList(),
            recentSpending = 0
        )
    }
}
