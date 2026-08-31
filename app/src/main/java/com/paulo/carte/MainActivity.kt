package com.paulo.carte

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.text.TextUtils
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.ceil

data class LoyaltyCard(
    val name: String,
    val number: String,
    val category: String = "Autres",
    val photoBase64: String = ""
)

data class AdOffer(
    val title: String,
    val subtitle: String,
    val url: String,
    var enabled: Boolean = true
)

class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var securityStore: SecurityStore
    private val cards = mutableListOf<LoyaltyCard>()
    private val ads = mutableListOf<AdOffer>()
    private var unlocked = false
    private var currentScreen = "home"
    private var pendingPhotoFile: File? = null

    private val navy = Color.rgb(5, 20, 35)
    private val navy2 = Color.rgb(8, 31, 54)
    private val gold = Color.rgb(244, 190, 75)
    private val blue = Color.rgb(10, 95, 198)
    private val white = Color.WHITE
    private val muted = Color.rgb(173, 185, 197)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        securityStore = SecurityStore(this)
        loadAds()
        if (securityStore.hasPassword()) {
            showUnlockScreen()
            tryBiometricUnlock()
        } else showCreatePasswordScreen()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, radius: Int = 18, strokeColor: Int? = null, strokeWidth: Int = 1) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            strokeColor?.let { setStroke(dp(strokeWidth), it) }
        }

    private fun baseRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(14))
        setBackgroundColor(navy)
    }

    private fun text(value: String, size: Float = 16f, color: Int = white, center: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = if (center) Gravity.CENTER else Gravity.START
    }

    private fun title(value: String, size: Float = 28f) = text(value, size, white, true).apply {
        setPadding(0, dp(16), 0, dp(12))
    }

    private fun button(label: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 16f
        isAllCaps = false
        setTextColor(if (primary) navy else white)
        background = shape(if (primary) gold else navy2, 14, if (primary) gold else Color.rgb(43, 71, 96))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setOnClickListener { onClick() }
    }

    private fun passwordField(hintText: String) = EditText(this).apply {
        hint = hintText
        setHintTextColor(muted)
        setTextColor(white)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        background = shape(Color.rgb(22, 33, 44), 12, Color.rgb(58, 73, 89))
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    private fun addSpace(parent: LinearLayout, height: Int = 10) =
        parent.addView(Space(this), LinearLayout.LayoutParams(1, dp(height)))

    private fun showCreatePasswordScreen() {
        currentScreen = "auth"
        root = baseRoot().apply { gravity = Gravity.CENTER_HORIZONTAL }
        root.addView(text("👛", 72f, gold, true))
        root.addView(title("PAULO PORTE CARTE", 29f))
        root.addView(text("Tes cartes, toujours avec toi", 16f, muted, true))
        addSpace(root, 24)
        root.addView(text("Crée ton mot de passe", 20f, white, true))
        root.addView(text("8 caractères minimum avec lettre, chiffre et symbole @ ! #", 14f, muted, true).apply { setPadding(0, dp(8), 0, dp(16)) })
        val password = passwordField("Nouveau mot de passe")
        val confirmation = passwordField("Confirmer le mot de passe")
        root.addView(password, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addSpace(root)
        root.addView(confirmation, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addSpace(root, 14)
        root.addView(button("Créer mon mot de passe", true) {
            val p1 = password.text.toString()
            val p2 = confirmation.text.toString()
            when {
                !AuthPolicy.isStrongPassword(p1) -> Toast.makeText(this, "Mot de passe trop faible", Toast.LENGTH_LONG).show()
                p1 != p2 -> Toast.makeText(this, "Les mots de passe sont différents", Toast.LENGTH_LONG).show()
                else -> { securityStore.savePassword(p1); unlockAndOpen() }
            }
        })
        setContentView(root)
    }

    private fun showUnlockScreen() {
        currentScreen = "auth"
        unlocked = false
        root = baseRoot().apply { gravity = Gravity.CENTER_HORIZONTAL }
        root.addView(text("🔐", 64f, gold, true))
        root.addView(title("PAULO PORTE CARTE", 29f))
        root.addView(text("Bienvenue !", 20f, white, true))
        root.addView(text("Connecte-toi pour continuer", 14f, muted, true).apply { setPadding(0, dp(6), 0, dp(18)) })
        val password = passwordField("Mot de passe")
        root.addView(password, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addSpace(root)
        root.addView(button("Se connecter", true) { unlockWithPassword(password.text.toString()) })
        addSpace(root)
        root.addView(button("Empreinte / Visage") { tryBiometricUnlock() }.apply { isEnabled = biometricAvailable() })
        setContentView(root)
    }

    private fun unlockWithPassword(password: String) {
        if (securityStore.isLocked()) {
            val seconds = ceil((securityStore.lockUntil() - System.currentTimeMillis()) / 1000.0).toInt().coerceAtLeast(1)
            Toast.makeText(this, "Trop d'essais. Réessaie dans $seconds secondes.", Toast.LENGTH_LONG).show()
            return
        }
        if (securityStore.verifyPassword(password)) unlockAndOpen()
        else if (securityStore.isLocked()) Toast.makeText(this, "5 erreurs : bloquée 30 secondes.", Toast.LENGTH_LONG).show()
        else Toast.makeText(this, "Mot de passe incorrect", Toast.LENGTH_SHORT).show()
    }

    private fun biometricAvailable() =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    private fun tryBiometricUnlock() {
        if (!biometricAvailable()) return
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                securityStore.resetFailures()
                unlockAndOpen()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("PAULO PORTE CARTE")
            .setSubtitle("Empreinte digitale ou reconnaissance du visage")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText("Mot de passe")
            .build()
        prompt.authenticate(info)
    }

    private fun unlockAndOpen() {
        unlocked = true
        loadCards()
        showWalletHome()
    }

    private fun topBar(screenTitle: String, showBack: Boolean = false): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        if (showBack) addView(button("← Retour") { showWalletHome() }, LinearLayout.LayoutParams(dp(112), dp(48)))
        addView(text(screenTitle, 20f, white, true), LinearLayout.LayoutParams(0, dp(48), 1f))
        if (!showBack) addView(button("☰") { showProfile() }, LinearLayout.LayoutParams(dp(58), dp(48)))
    }

    private fun showWalletHome() {
        if (!unlocked) return showUnlockScreen()
        currentScreen = "home"
        root = baseRoot()
        root.addView(topBar("Mon portefeuille"))
        val security = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = shape(blue, 14)
            addView(text("Vos cartes, en toute sécurité", 16f, white))
            addView(text("Stockage local protégé • capture d'écran bloquée", 12f, Color.rgb(212, 231, 255)))
        }
        addSpace(root, 12)
        root.addView(security)
        showAdvertisingStrip(root)
        root.addView(text("Mes cartes (${cards.size})", 18f, white).apply { setPadding(0, dp(18), 0, dp(10)) })
        val scroll = ScrollView(this)
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (cards.isEmpty()) holder.addView(text("Aucune carte.\nScanne ta première carte entière.", 17f, muted, true).apply { setPadding(0, dp(35), 0, dp(35)) })
        else cards.take(8).forEach { holder.addView(cardRow(it)) }
        scroll.addView(holder)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(button("📷 Scanner une carte entière", true) { startFullCardScan() })
        addSpace(root, 10)
        root.addView(bottomNav("Accueil"))
        setContentView(root)
    }

    private fun showAdvertisingStrip(parent: LinearLayout) {
        val ad = ads.firstOrNull { it.enabled && WalletUiLogic.isValidWebUrl(it.url) } ?: return
        addSpace(parent, 10)
        val banner = button("PUBLICITÉ  •  ${ad.title}") { openAd(ad) }.apply { gravity = Gravity.CENTER }
        parent.addView(banner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        val ticker = text(ad.subtitle.ifBlank { ad.title }, 13f, gold, true).apply {
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            setPadding(dp(8), dp(6), dp(8), dp(4))
        }
        parent.addView(ticker, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)))
    }

    private fun cardRow(card: LoyaltyCard): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = shape(merchantColor(card.name), 16)
        if (card.photoBase64.isNotEmpty()) {
            decodeBitmap(card.photoBase64)?.let { bmp ->
                addView(ImageView(this@MainActivity).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = shape(Color.WHITE, 10)
                }, LinearLayout.LayoutParams(dp(86), dp(54)))
            }
        } else {
            val badge = text(WalletUiLogic.badgeForMerchant(card.name), 18f, white, true).apply { background = shape(Color.argb(70, 255, 255, 255), 30) }
            addView(badge, LinearLayout.LayoutParams(dp(54), dp(54)))
        }
        val details = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(text(card.name.uppercase(), 18f, white))
            addView(text(card.category, 12f, Color.rgb(220, 232, 245)))
        }
        addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        setOnClickListener { showCard(card) }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80))
        lp.bottomMargin = dp(9)
        layoutParams = lp
    }

    private fun merchantColor(name: String): Int {
        val n = name.lowercase()
        return when {
            n.contains("super u") -> Color.rgb(3, 93, 191)
            n.contains("intermarch") -> Color.rgb(194, 46, 61)
            n.contains("leclerc") -> Color.rgb(13, 86, 167)
            n.contains("carrefour") -> Color.rgb(0, 66, 145)
            else -> Color.rgb(28, 78, 118)
        }
    }

    private fun bottomNav(active: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        listOf(
            "Accueil" to { showWalletHome() },
            "Catégories" to { showCategories() },
            "Scanner" to { startFullCardScan() },
            "Offres" to { showOffers() },
            "Profil" to { showProfile() }
        ).forEach { (label, action) ->
            addView(Button(this@MainActivity).apply {
                text = label
                textSize = 10f
                isAllCaps = false
                setTextColor(if (label == active) gold else muted)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, dp(54), 1f))
        }
    }

    private fun startFullCardScan() {
        AlertDialog.Builder(this)
            .setTitle("Scanner la carte entière")
            .setMessage("Place toute la carte dans l'écran : logo, nom du commerce et code-barres/QR doivent être visibles. Une seule photo sera analysée.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Ouvrir la caméra") { _, _ -> captureFullCard() }
            .show()
    }

    private fun captureFullCard() {
        val directory = File(cacheDir, "card_scans").apply { mkdirs() }
        val photo = File(directory, "card_${System.currentTimeMillis()}.jpg")
        pendingPhotoFile = photo
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photo)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, "Appareil photo indisponible", Toast.LENGTH_LONG).show()
            return
        }
        startActivityForResult(intent, 501)
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 501) return
        if (resultCode != RESULT_OK) {
            pendingPhotoFile?.delete()
            pendingPhotoFile = null
            return
        }
        val file = pendingPhotoFile
        if (file == null || !file.exists() || file.length() == 0L) {
            Toast.makeText(this, "Photo de la carte introuvable", Toast.LENGTH_LONG).show()
            return
        }
        val bitmap = decodeCardPhoto(file)
        if (bitmap == null) {
            Toast.makeText(this, "Impossible de lire la photo", Toast.LENGTH_LONG).show()
            return
        }
        processCapturedCard(bitmap)
    }

    private fun decodeCardPhoto(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1800 || bounds.outHeight / sample > 1800) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun processCapturedCard(bitmap: Bitmap) {
        Toast.makeText(this, "Analyse de la carte complète…", Toast.LENGTH_SHORT).show()
        val image = InputImage.fromBitmap(bitmap, 0)
        val barcodeScanner = BarcodeScanning.getClient()
        val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        barcodeScanner.process(image).addOnCompleteListener { barcodeTask ->
            val number = if (barcodeTask.isSuccessful) barcodeTask.result?.firstOrNull()?.rawValue.orEmpty().trim() else ""
            textRecognizer.process(image).addOnCompleteListener { textTask ->
                val scannedText = if (textTask.isSuccessful) textTask.result?.text.orEmpty() else ""
                val merchant = WalletUiLogic.recognizeMerchantFromText(scannedText)
                showFullCardPreview(bitmap, merchant, number)
                barcodeScanner.close()
                textRecognizer.close()
            }
        }
    }

    private fun showFullCardPreview(bitmap: Bitmap, detectedMerchant: String, detectedNumber: String) {
        val photoBase64 = bitmapToBase64(bitmap)
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(8)) }
        box.addView(ImageView(this).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = shape(Color.WHITE, 14)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)))
        box.addView(text("Aperçu de la carte complète", 15f, Color.DKGRAY, true).apply { setPadding(0, dp(8), 0, dp(8)) })
        val name = EditText(this).apply {
            hint = "Nom du commerce (ex. Carrefour)"
            setText(detectedMerchant)
        }
        val code = EditText(this).apply {
            hint = "Code-barres / QR"
            setText(detectedNumber)
        }
        box.addView(name)
        box.addView(code)
        scroll.addView(box)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Carte détectée")
            .setView(scroll)
            .setNegativeButton("Reprendre la photo") { _, _ -> startFullCardScan() }
            .setNeutralButton("Annuler", null)
            .setPositiveButton("Enregistrer", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val merchant = name.text.toString().trim()
                val value = code.text.toString().trim()
                if (merchant.isEmpty()) {
                    name.error = "Indique le nom du commerce"
                    return@setOnClickListener
                }
                if (value.isEmpty()) {
                    code.error = "Code non détecté : saisis-le ou reprends la photo"
                    return@setOnClickListener
                }
                cards.add(LoyaltyCard(merchant, value, WalletUiLogic.categoryForMerchant(merchant), photoBase64))
                saveCards()
                pendingPhotoFile?.delete()
                pendingPhotoFile = null
                dialog.dismiss()
                showWalletHome()
            }
        }
        dialog.show()
    }

    private fun scanBarcodeOnly() {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).enableAutoZoom().build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { barcode -> askCardNameAfterScan(barcode.rawValue.orEmpty().trim()) }
            .addOnFailureListener { Toast.makeText(this, "Impossible de scanner le code", Toast.LENGTH_LONG).show() }
    }

    private fun askCardNameAfterScan(number: String) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0) }
        val name = EditText(this).apply { hint = "Nom du commerce" }
        val code = EditText(this).apply { hint = "Numéro / code"; setText(number) }
        box.addView(name)
        box.addView(code)
        AlertDialog.Builder(this).setTitle("Ajouter la carte").setView(box)
            .setPositiveButton("Ajouter") { _, _ ->
                val merchant = name.text.toString().trim()
                val value = code.text.toString().trim()
                if (merchant.isNotEmpty() && value.isNotEmpty()) {
                    cards.add(LoyaltyCard(merchant, value, WalletUiLogic.categoryForMerchant(merchant)))
                    saveCards()
                    showWalletHome()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showCard(card: LoyaltyCard) {
        currentScreen = "card"
        root = baseRoot()
        root.addView(topBar(card.name, true))
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(18), 0, dp(18)) }
        if (card.photoBase64.isNotEmpty()) {
            decodeBitmap(card.photoBase64)?.let { bmp ->
                content.addView(ImageView(this).apply {
                    setImageBitmap(bmp)
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = shape(Color.WHITE, 18)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)))
                addSpace(content, 14)
            }
        } else {
            content.addView(text(WalletUiLogic.badgeForMerchant(card.name), 64f, white, true).apply { background = shape(merchantColor(card.name), 22) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)))
            addSpace(content, 14)
        }
        content.addView(text(card.name.uppercase(), 27f, white, true))
        content.addView(text(card.category, 14f, muted, true).apply { setPadding(0, dp(4), 0, dp(18)) })
        content.addView(ImageView(this).apply {
            adjustViewBounds = true
            setImageBitmap(makeBarcode(card.number))
            background = shape(Color.WHITE, 12)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)))
        content.addView(text(card.number, 16f, white, true).apply { setPadding(0, dp(10), 0, dp(20)) })
        content.addView(button("Supprimer cette carte") { confirmDelete(card) })
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun showCategories() {
        currentScreen = "categories"
        root = baseRoot()
        root.addView(topBar("Catégories", true))
        val all = listOf("Supermarchés", "Carburant", "Mode", "Loisirs", "Autres")
        val icons = mapOf("Supermarchés" to "🛒", "Carburant" to "⛽", "Mode" to "👕", "Loisirs" to "🎟", "Autres" to "💳")
        all.forEach { cat ->
            val count = cards.count { it.category == cat }
            root.addView(button("${icons[cat]}   $cat     ($count)") { showCategoryCards(cat) }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL })
            addSpace(root, 8)
        }
        root.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(bottomNav("Catégories"))
        setContentView(root)
    }

    private fun showCategoryCards(category: String) {
        root = baseRoot()
        root.addView(topBar(category, true))
        val scroll = ScrollView(this)
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, 0) }
        cards.filter { it.category == category }.forEach { holder.addView(cardRow(it)) }
        if (holder.childCount == 0) holder.addView(text("Aucune carte dans cette catégorie", 16f, muted, true).apply { setPadding(0, dp(40), 0, 0) })
        scroll.addView(holder)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun showOffers() {
        currentScreen = "offers"
        root = baseRoot()
        root.addView(topBar("Offres", true))
        root.addView(text("Publicités partenaires", 18f, white).apply { setPadding(0, dp(14), 0, dp(10)) })
        val scroll = ScrollView(this)
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val enabled = ads.filter { it.enabled && WalletUiLogic.isValidWebUrl(it.url) }
        if (enabled.isEmpty()) holder.addView(text("Aucune publicité active pour le moment.", 16f, muted, true).apply { setPadding(0, dp(40), 0, 0) })
        enabled.forEach { ad ->
            holder.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = shape(navy2, 16, Color.rgb(44, 72, 98))
                addView(text(ad.title, 20f, white))
                addView(text(ad.subtitle, 14f, muted).apply { setPadding(0, dp(5), 0, dp(10)) })
                addView(button("Découvrir l'offre", true) { openAd(ad) })
            })
            addSpace(holder, 10)
        }
        scroll.addView(holder)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNav("Offres"))
        setContentView(root)
    }

    private fun showProfile() {
        currentScreen = "profile"
        root = baseRoot()
        root.addView(topBar("Profil & paramètres", true))
        root.addView(text("👤", 60f, gold, true))
        root.addView(text("Paulo", 24f, white, true).apply { setPadding(0, dp(6), 0, dp(18)) })
        root.addView(text("L'administration des publicités est séparée et n'est pas accessible depuis l'application client.", 13f, muted, true).apply { setPadding(dp(8), 0, dp(8), dp(18)) })
        root.addView(button("▥ Scanner seulement un code-barres") { scanBarcodeOnly() })
        addSpace(root)
        root.addView(button("🔒 Verrouiller l'application") { showUnlockScreen() })
        addSpace(root)
        root.addView(button("🚪 Quitter l'application") { confirmQuit() })
        root.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(bottomNav("Profil"))
        setContentView(root)
    }

    private fun openAd(ad: AdOffer) {
        if (!WalletUiLogic.isValidWebUrl(ad.url)) return
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ad.url))) }
        catch (_: Exception) { Toast.makeText(this, "Lien impossible à ouvrir", Toast.LENGTH_SHORT).show() }
    }

    private fun confirmQuit() {
        AlertDialog.Builder(this)
            .setTitle("Quitter l'application ?")
            .setMessage("Veux-tu vraiment quitter PAULO PORTE CARTE ?")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Quitter") { _, _ -> finishAffinity() }
            .show()
    }

    private fun confirmDelete(card: LoyaltyCard) {
        AlertDialog.Builder(this)
            .setTitle("Supprimer ${card.name} ?")
            .setMessage("La carte sera supprimée du portefeuille.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer") { _, _ -> cards.remove(card); saveCards(); showWalletHome() }
            .show()
    }

    private fun makeBarcode(value: String): Bitmap {
        val width = 1200
        val height = 420
        val format = if (value.length in 1..70) BarcodeFormat.CODE_128 else BarcodeFormat.QR_CODE
        val matrix = try { MultiFormatWriter().encode(value, format, width, height) }
        catch (_: Exception) { MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 600, 600) }
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565)
        for (x in 0 until matrix.width) for (y in 0 until matrix.height) bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        return bitmap
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun decodeBitmap(encoded: String): Bitmap? = try {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) { null }

    private fun saveCards() {
        val array = JSONArray()
        cards.forEach { card ->
            array.put(JSONObject().apply {
                put("name", card.name)
                put("number", card.number)
                put("category", card.category)
                put("photo", card.photoBase64)
            })
        }
        securityStore.saveCardsJson(array.toString())
    }

    private fun loadCards() {
        cards.clear()
        try {
            val array = JSONArray(securityStore.loadCardsJson())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val name = o.optString("name")
                cards.add(LoyaltyCard(name, o.optString("number"), o.optString("category").ifBlank { WalletUiLogic.categoryForMerchant(name) }, o.optString("photo")))
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Impossible de lire les cartes enregistrées", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadAds() {
        ads.clear()
        val raw = getSharedPreferences("paulo_ads", MODE_PRIVATE).getString("ads", "[]") ?: "[]"
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                ads.add(AdOffer(o.optString("title"), o.optString("subtitle"), o.optString("url"), o.optBoolean("enabled", true)))
            }
        } catch (_: Exception) { }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!unlocked) super.onBackPressed()
        else if (currentScreen == "home") confirmQuit()
        else showWalletHome()
    }
}
