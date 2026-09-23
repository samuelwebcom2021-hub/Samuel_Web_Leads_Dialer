package com.tuempresa.autodialer.dialer

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager

data class SimOption(val handle: PhoneAccountHandle, val label: String)

/**
 * Gestiona la detección y validación de SIMs/Líneas de llamada en el dispositivo.
 */
object SimSelector {

    @SuppressLint("MissingPermission")
    fun listAvailableSims(context: Context): List<SimOption> {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        
        val sims = mutableListOf<SimOption>()
        try {
            val handles = telecomManager.callCapablePhoneAccounts
            val subscriptions = subscriptionManager.activeSubscriptionInfoList ?: emptyList()

            sims.addAll(handles.mapNotNull { handle ->
                val account = telecomManager.getPhoneAccount(handle) ?: return@mapNotNull null
                
                val subInfo = subscriptions.find { sub ->
                    handle.id.contains(sub.subscriptionId.toString()) || 
                    handle.id.contains(sub.simSlotIndex.toString())
                }

                val operatorName = account.label?.toString() ?: subInfo?.carrierName?.toString() ?: "Desconocido"
                val slotIndex = subInfo?.simSlotIndex?.let { it + 1 }
                
                val finalLabel = if (slotIndex != null) {
                    "SIM $slotIndex — $operatorName"
                } else {
                    operatorName
                }

                SimOption(handle, finalLabel)
            })
        } catch (e: SecurityException) {
            // Ignorar
        }

        // Simular SIM si estamos en modo prueba o no hay reales (para el usuario)
        if (sims.isEmpty()) {
            val componentName = ComponentName(context, "com.tuempresa.autodialer.FakeConnectionService")
            val handle = PhoneAccountHandle(componentName, "FakeSimID")
            sims.add(SimOption(handle, "SIM 1 — Operador Virtual (Prueba)"))
        }

        return sims
    }

    /**
     * Verifica si un PhoneAccountHandle guardado sigue siendo válido y está disponible.
     */
    fun isHandleValid(context: Context, handle: PhoneAccountHandle): Boolean {
        if (handle.id == "FakeSimID") return true
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return try {
            val account = telecomManager.getPhoneAccount(handle)
            account?.isEnabled == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reconstruye un PhoneAccountHandle a partir de los strings guardados en settings.
     */
    fun getHandleFromStrings(component: String?, id: String?): PhoneAccountHandle? {
        if (component == null || id == null) return null
        return try {
            val cn = ComponentName.unflattenFromString(component) ?: return null
            PhoneAccountHandle(cn, id)
        } catch (e: Exception) {
            null
        }
    }
}
