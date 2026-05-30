package com.xiao.pocketir.ui.screens

import android.hardware.ConsumerIrManager
import androidx.compose.runtime.Composable
import com.xiao.pocketir.model.DeviceMeta
import com.xiao.pocketir.ui.panels.GridPanel

@Composable
fun PanelScreen(
    irManager: ConsumerIrManager?,
    device: DeviceMeta
) {
    GridPanel(irManager = irManager, device = device)
}
