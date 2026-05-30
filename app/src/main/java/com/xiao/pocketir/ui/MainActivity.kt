package com.xiao.pocketir.ui

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.xiao.pocketir.i18n.I18n

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        I18n.init(applicationContext)

        val irManager = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        setContent {
            AppRoot(irManager = irManager)
        }
    }
}
