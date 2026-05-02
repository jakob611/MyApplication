package com.example.myapplication.utils

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code

/**
 * FirestoreErrorHandler — globalni handler za kritične Firestore napake.
 *
 * Namesto da aplikacija tiho "obmiruje" pri kritičnih napakah Firestore-a,
 * ta handler prikaže prijazen Toast s pojasnilom in predlogom za rešitev.
 *
 * Uporaba:
 *   FirestoreErrorHandler.handle(context, exception, "RunTrackerScreen")
 *
 * Integrirano v:
 *   - AppViewModel.startInitialSync()
 *   - RunTrackerScreen (shranjevanje seje)
 */
object FirestoreErrorHandler {

    private const val TAG = "FirestoreErrorHandler"

    /**
     * Obdela Firestore izjemo in prikaže prijazen Toast.
     * @param context Android Context za prikaz Toasta
     * @param e       ujeta izjema
     * @param source  ime komponente, ki je sprožila klic (za logiranje)
     * @return true = bila je kritična Firestore napaka; false = navadna Exception
     */
    fun handle(context: Context, e: Exception, source: String = "?"): Boolean {
        if (e is FirebaseFirestoreException) {
            val msg = when (e.code) {
                Code.PERMISSION_DENIED ->
                    "Dostop zavrnjen. Preveri, ali si prijavljen, ali potrdi e-poštni naslov."
                Code.UNAVAILABLE ->
                    "Strežnik ni dosegljiv. Preveri internetno povezavo."
                Code.UNAUTHENTICATED ->
                    "Seja je potekla. Odjavi se in se znova prijavi."
                Code.RESOURCE_EXHAUSTED ->
                    "Preveč zahtev. Počakaj minuto in poskusi znova."
                Code.FAILED_PRECONDITION ->
                    "Podatki so preveliki za shranjevanje. Kontaktiraj podporo."
                Code.NOT_FOUND ->
                    "Dokument ni bil najden v bazi. Poizkusi osvežiti stran."
                Code.ALREADY_EXISTS ->
                    "Zapis že obstaja. Osveži in poizkusi znova."
                Code.DEADLINE_EXCEEDED ->
                    "Zahteva je trajala predolgo. Preveri internetno povezavo."
                else ->
                    "Napaka strežnika (${e.code.name}). Poizkusi znova."
            }
            Log.e(TAG, "[$source] Firestore ${e.code}: ${e.message}")
            AppToast.showError(context, msg)
            return true
        }
        // Navadna mrežna / IOException — logiraj, ne prikazuj technicalities
        Log.e(TAG, "[$source] Splošna napaka: ${e.message}")
        return false
    }

    /**
     * Async wrapper — poženi Firestore operacijo in samodejno obdela napako.
     *
     * Primer:
     *   val ok = FirestoreErrorHandler.runSafe(context, "ProfileSave") {
     *       docRef.set(data, SetOptions.merge()).await()
     *   }
     */
    suspend fun <T> runSafe(
        context: Context,
        source: String,
        block: suspend () -> T
    ): T? = try {
        block()
    } catch (e: FirebaseFirestoreException) {
        handle(context, e, source)
        null
    } catch (e: Exception) {
        Log.e(TAG, "[$source] Nepričakovana napaka: ${e.message}", e)
        AppToast.showError(context, "Nepričakovana napaka. Poizkusi znova.")
        null
    }
}

