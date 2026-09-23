package com.tuempresa.autodialer.data.sync

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.data.BatchEntity
import com.tuempresa.autodialer.data.ContactEntity
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RestoreManager(private val context: Context) {

    private val db = (context.applicationContext as App).db
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun restoreAll() = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        val userDoc = firestore.collection("users").document(uid)

        try {
            val foldersSnapshot = userDoc.collection("folders").get().await()
            for (folderDoc in foldersSnapshot.documents) {
                val folderId = folderDoc.id.toLong()
                val folderName = folderDoc.getString("name") ?: "Carpeta restaurada"
                val isArchived = folderDoc.getBoolean("isArchived") ?: false
                
                val folder = BatchEntity(id = folderId, name = folderName, isArchived = isArchived)
                db.batchDao().insert(folder)

                val contactsSnapshot = folderDoc.reference.collection("contacts").get().await()
                val contacts = contactsSnapshot.documents.mapNotNull { contactDoc ->
                    val contactId = contactDoc.id.toLong()
                    contactDoc.toObject(ContactEntity::class.java)?.copy(id = contactId)
                }
                
                if (contacts.isNotEmpty()) {
                    db.contactDao().insertAll(contacts)
                    // Restoring extra fields and attempts could be added here
                }
            }
            Log.d("RestoreManager", "Restauración completada con éxito")
        } catch (e: Exception) {
            Log.e("RestoreManager", "Error durante la restauración", e)
        }
    }
}
