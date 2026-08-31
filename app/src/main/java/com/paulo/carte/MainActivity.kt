package com.paulo.carte

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.gms.mlkit.codescanner.GmsBarcodeScannerOptions
import com.google.android.gms.mlkit.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

data class LoyaltyCard(val name: String, val number: String)

class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var listContainer: LinearLayout
    private lateinit var securityStore: SecurityStore
    private val cards = mutableListOf<LoyaltyCard>()
    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        securityStore = SecurityStore(this)

        if (securityStore.hasPassword()) {
            showUnlockScreen()
            tryBiometricUnlock()
        } else {
            showCreatePasswordScreen()
        }
    }

    private fun baseRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(28, 28, 28, 28)
        setBackgroundColor(Color.rgb(247, 247, 247))
    }

    private fun title(textValue: String, size: Float = 30f): TextView = TextView(this).apply {
        text = textValue
        textSize = size
        setTextColor(Color.BLACK)
        gravity = Gravity.CENTER
        setPadding(0, 24, 0, 18)
    }

    private fun passwordField(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun showCreatePasswordScreen() {
        root = baseRoot()
        root.addView(title("Sécuriser Portefeuille Carte", 27f))

        val help = TextView(this).apply {
            text = "Choisis au moins 8 caractères avec une lettre, un chiffre et un symbole comme @ ! #"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 22)
        }
        val password = passwordField("Nouveau mot de passe")
        val confirmation = passwordField("Confirmer le mot de passe")
        val create = Button(this).apply {
            text = "Créer mon mot de passe"
            isAllCaps = false
            setOnClickListener {
                val p1 = password.text.toString()
                val p2 = confirmation.text.toString()
                when {
                    !AuthPolicy.isStrongPassword(p1) -> Toast.makeText(this@MainActivity, "Mot de passe trop faible", Toast.LENGTH_LONG).show()
                    p1 != p2 -> Toast.makeText(this@MainActivity, "Les deux mots de passe sont différents", Toast.LENGTH_LONG).show()
                    else -> {
                        securityStore.savePassword(p1)
                        unlockAndOpen()
                    }
                }
            }
        }

        root.addView(help)
        root.addView(password)
        root.addView(confirmation)
        root.addView(create)
        setContentView(root)
    }

    private fun showUnlockScreen() {
        unlocked = false
        root = baseRoot()
        root.addView(title("Portefeuille Carte verrouillé", 27f))

        val info = TextView(this).apply {
            text = "Déverrouille avec ton empreinte, ton visage ou ton mot de passe."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        val password = passwordField("Mot de passe")
        val unlock = Button(this).apply {
            text = "Déverrouiller"
            isAllCaps = false
            setOnClickListener { unlockWithPassword(password.text.toString()) }
        }
        val biometric = Button(this).apply {
            text = "Empreinte / visage"
            isAllCaps = false
            isEnabled = biometricAvailable()
            setOnClickListener { tryBiometricUnlock() }
        }

        root.addView(info)
        root.addView(password)
        root.addView(unlock)
        root.addView(biometric)
        setContentView(root)
    }

    private fun unlockWithPassword(password: String) {
        if (securityStore.isLocked()) {
            val seconds = ceil((securityStore.lockUntil() - System.currentTimeMillis()) / 1000.0).toInt().coerceAtLeast(1)
            Toast.makeText(this, "Trop d'essais. Réessaie dans $seconds secondes.", Toast.LENGTH_LONG).show()
            return
        }
        if (securityStore.verifyPassword(password)) {
            unlockAndOpen()
        } else if (securityStore.isLocked()) {
            Toast.makeText(this, "5 erreurs : application bloquée 30 secondes.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Mot de passe incorrect", Toast.LENGTH_SHORT).show()
        }
    }

    private fun biometricAvailable(): Boolean {
        return BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun tryBiometricUnlock() {
        if (!biometricAvailable()) return
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                securityStore.resetFailures()
                unlockAndOpen()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                    Toast.makeText(this@MainActivity, "Biométrie indisponible : utilise le mot de passe", Toast.LENGTH_SHORT).show()
                }
            }
        })

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Déverrouiller Portefeuille Carte")
            .setSubtitle("Empreinte digitale ou reconnaissance du visage")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText("Utiliser le mot de passe")
            .build()
        prompt.authenticate(info)
    }

    private fun unlockAndOpen() {
        unlocked = true
        loadCards()
        showWalletHome()
    }

    private fun showWalletHome() {
        if (!unlocked) return showUnlockScreen()
        root = baseRoot()

        val logo = TextView(this).apply {
            text = "👛"
            textSize = 76f
            gravity = Gravity.CENTER
            setPadding(0, 50, 0, 10)
        }
        val appTitle = title("PORTEFEUILLE", 32f)
        val subtitle = TextView(this).apply {
            text = "Toutes mes cartes au même endroit"
            textSize = 17f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 35)
        }
        val cardsButton = Button(this).apply {
            text = "💳  MES CARTES"
            textSize = 20f
            isAllCaps = false
            setOnClickListener { showCardsScreen() }
        }
        val scanButton = Button(this).apply {
            text = "📷  Scanner une carte"
            textSize = 18f
            isAllCaps = false
            setOnClickListener { scanCard() }
        }
        val addButton = Button(this).apply {
            text = "➕  Ajouter une carte"
            textSize = 18f
            isAllCaps = false
            setOnClickListener { showAddDialog() }
        }
        val lockButton = Button(this).apply {
            text = "🔒 Verrouiller"
            isAllCaps = false
            setOnClickListener { showUnlockScreen() }
        }
        val count = TextView(this).apply {
            text = if (cards.isEmpty()) "Aucune carte enregistrée" else "${cards.size} carte${if (cards.size > 1) "s" else ""} enregistrée${if (cards.size > 1) "s" else ""}"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
            setPadding(0, 30, 0, 0)
        }

        root.addView(logo)
        root.addView(appTitle)
        root.addView(subtitle)
        root.addView(cardsButton)
        root.addView(scanButton)
        root.addView(addButton)
        root.addView(lockButton)
        root.addView(count)
        setContentView(root)
    }

    private fun showCardsScreen() {
        if (!unlocked) return showUnlockScreen()
        root = baseRoot()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = Button(this).apply { text = "←"; textSize = 22f; setOnClickListener { showWalletHome() } }
        val screenTitle = TextView(this).apply { text = "MES CARTES"; textSize = 27f; setTextColor(Color.BLACK); gravity = Gravity.CENTER; setPadding(14, 15, 0, 15) }
        top.addView(back, LinearLayout.LayoutParams(110, ViewGroup.LayoutParams.WRAP_CONTENT))
        top.addView(screenTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val scan = Button(this).apply { text = "📷 Scanner"; isAllCaps = false; setOnClickListener { scanCard() } }
        val add = Button(this).apply { text = "➕ Ajouter"; isAllCaps = false; setOnClickListener { showAddDialog() } }
        val scroll = ScrollView(this)
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 20, 0, 20) }
        scroll.addView(listContainer)
        root.addView(top)
        root.addView(scan)
        root.addView(add)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        refreshList()
    }

    private fun scanCard() {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).enableAutoZoom().build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.rawValue.orEmpty().trim()
                if (value.isNotEmpty()) askCardNameAfterScan(value) else Toast.makeText(this, "Code non lisible", Toast.LENGTH_SHORT).show()
            }
            .addOnCanceledListener { Toast.makeText(this, "Scan annulé", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { Toast.makeText(this, "Impossible de scanner ce code", Toast.LENGTH_LONG).show() }
    }

    private fun askCardNameAfterScan(number: String) {
        val input = EditText(this).apply { hint = "Nom du magasin (ex. Carrefour)"; inputType = InputType.TYPE_CLASS_TEXT }
        AlertDialog.Builder(this)
            .setTitle("Ajouter la carte scannée")
            .setMessage("Code détecté : $number")
            .setView(input)
            .setPositiveButton("Ajouter") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) { cards.add(LoyaltyCard(name, number)); saveCards(); showCardsScreen() }
                else Toast.makeText(this, "Entre le nom du magasin", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showAddDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 12, 40, 0) }
        val nameInput = EditText(this).apply { hint = "Nom du magasin"; inputType = InputType.TYPE_CLASS_TEXT }
        val numberInput = EditText(this).apply { hint = "Numéro / code de la carte"; inputType = InputType.TYPE_CLASS_TEXT }
        box.addView(nameInput); box.addView(numberInput)
        AlertDialog.Builder(this).setTitle("Ajouter une carte").setView(box)
            .setPositiveButton("Ajouter") { _, _ ->
                val name = nameInput.text.toString().trim(); val number = numberInput.text.toString().trim()
                if (name.isNotEmpty() && number.isNotEmpty()) { cards.add(LoyaltyCard(name, number)); saveCards(); showCardsScreen() }
                else Toast.makeText(this, "Nom et numéro obligatoires", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annuler", null).show()
    }

    private fun refreshList() {
        listContainer.removeAllViews()
        if (cards.isEmpty()) {
            listContainer.addView(TextView(this).apply { text = "Aucune carte.\nScanne ou ajoute ta première carte."; textSize = 17f; gravity = Gravity.CENTER; setPadding(0, 70, 0, 20) })
            return
        }
        cards.forEachIndexed { index, card ->
            listContainer.addView(Button(this).apply {
                text = "💳  ${card.name}"; textSize = 19f; isAllCaps = false
                setOnClickListener { showCard(card) }
                setOnLongClickListener { confirmDelete(index, card); true }
            })
        }
    }

    private fun showCard(card: LoyaltyCard) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(32, 20, 32, 20) }
        box.addView(TextView(this).apply { text = card.name; textSize = 24f; gravity = Gravity.CENTER; setTextColor(Color.BLACK) })
        box.addView(ImageView(this).apply { adjustViewBounds = true; setImageBitmap(makeBarcode(card.number)) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 300))
        box.addView(TextView(this).apply { text = card.number; textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.BLACK) })
        AlertDialog.Builder(this).setView(box).setPositiveButton("Fermer", null).show()
    }

    private fun makeBarcode(value: String): Bitmap {
        val width = 1200; val height = 420
        val matrix = MultiFormatWriter().encode(value, BarcodeFormat.CODE_128, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) for (y in 0 until height) bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        return bitmap
    }

    private fun confirmDelete(index: Int, card: LoyaltyCard) {
        AlertDialog.Builder(this).setTitle("Supprimer ${card.name} ?").setMessage("La carte sera supprimée du portefeuille.")
            .setPositiveButton("Supprimer") { _, _ -> cards.removeAt(index); saveCards(); showCardsScreen() }
            .setNegativeButton("Annuler", null).show()
    }

    private fun saveCards() {
        val array = JSONArray()
        cards.forEach { array.put(JSONObject().apply { put("name", it.name); put("number", it.number) }) }
        securityStore.saveCardsJson(array.toString())
    }

    private fun loadCards() {
        cards.clear()
        try {
            val array = JSONArray(securityStore.loadCardsJson())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                cards.add(LoyaltyCard(obj.optString("name"), obj.optString("number")))
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Impossible de lire les cartes enregistrées", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (unlocked) showWalletHome() else super.onBackPressed()
    }
}
