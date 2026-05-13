package com.example.saizeriya

import android.app.Application
import com.example.saizeriya.util.AppLogger

class SaizeriyaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        // Phase 3 で LiteRT-LM Engine 初期化を追加
    }
}
