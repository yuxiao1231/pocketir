package com.xiao.pocketir.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import android.widget.Toast
import com.xiao.pocketir.i18n.I18n
import com.xiao.pocketir.model.FlipperCommand

object IrTransmitter {
    fun transmit(
        context: Context,
        irManager: ConsumerIrManager?,
        command: FlipperCommand
    ): Boolean {
        if (irManager == null || !irManager.hasIrEmitter()) {
            Toast.makeText(context, I18n.t("error_no_ir"), Toast.LENGTH_SHORT).show()
            return false
        }

        val pattern = IrDecoder.buildPattern(command)
        if (pattern == null) {
            Toast.makeText(context, I18n.t("error_protocol_unsupported"), Toast.LENGTH_SHORT).show()
            return false
        }

        if (!IrCooldown.tryAcquire()) {
            return false
        }

        return runCatching {
            irManager.transmit(IrDecoder.resolveFrequency(command), pattern)
            Log.d("PocketIR", I18n.t("log_ir_fired", command.name, command.protocol ?: command.type.name))
            true
        }.getOrElse {
            Log.e("PocketIR", "IR transmit failed", it)
            false
        }
    }
}
