package com.paulo.carte

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.mlkit.codescanner.GmsBarcodeScannerOptions
import com.google.android.gms.mlkit.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.json.JSONArray
import org.json.JSONObject

data class LoyaltyCard(val name: String, val number: String)

class MainActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var listContainer: LinearLayout
    private val cards = mutableListOf<LoyaltyCard>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadCards()
        showWalletHome()
    }

    private fun baseRoot(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(247, 247, 247))
        }
    }

    private fun title(textValue: String, size: Float = 30f): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 18)
        }
    }

    private fun showWalletHome() {
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
            setPadding(20, 24, 20, 24)
            setOnClickListener { showCardsScreen() }
        }

        val scanButton = Button(this).apply {
            text = "📷  Scanner une carte"
            textSize = 18f
            isAllCaps = false
            setPadding(20, 20, 20, 20)
            setOnClickListener { scanCard() }
        }

        val addButton = Button(this).apply {
            text = "➕  Ajouter une carte"
            textSize = 18f
            isAllCaps = false
            setPadding(20, 20, 20, 20)
            setOnClickListener { showAddDialog() }
        }

        val count = TextView(this).apply {
            text = if (cards.isEmpty()) {
                "Aucune carte enregistrée"
            } else {
                "${cards.size} carte${if (cards.size > 1) "s" else ""} enregistrée${if (cards.size > 1) "s" else ""}"
            }
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
        root.addView(count)

        setContentView(root)
    }

    private fun showCardsScreen() {
        root = baseRoot()

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = Button(this).apply {
            text = "←"
            textSize = 22f
            setOnClickListener { showWalletHome() }
        }

        val screenTitle = TextView(this).apply {
            text = "MES CARTES"
            textSize = 27f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(14, 15, 0, 15)
        }

        top.addView(back, LinearLayout.LayoutParams(110, ViewGroup.LayoutParams.WRAP_CONTENT))
        top.addView(screenTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val scan = Button(this).apply {
            text = "📷 Scanner"
            textSize = 17f
            isAllCaps = false
            setOnClickListener { scanCard() }
        }

        val add = Button(this).apply {
            text = "➕ Ajouter"
            textSize = 17f
            isAllCaps = false
            setOnClickListener { showAddDialog() }
        }

        val scroll = ScrollView(this)
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 20)
        }
        scroll.addView(listContainer)

        root.addView(top)
        root.addView(scan)
        root.addView(add)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        setContentView(root)
        refreshList()
    }

    private fun scanCard() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(this, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.rawValue.orEmpty().trim()
                if (value.isNotEmpty()) {
                    askCardNameAfterScan(value)
                } else {
                    Toast.makeText(this, "Code non lisible", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnCanceledListener {
                Toast.makeText(this, "Scan annulé", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Impossible de scanner ce code", Toast.LENGTH_LONG).show()
            }
    }

    private fun askCardNameAfterScan(number: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 12, 40, 0)
        }

        val detected = TextView(this).apply {
            text = "Code détecté : $number"
            textSize = 15f
            setPadding(0, 0, 0, 14)
        }

        val nameInput = EditText(this).apply {
            hint = "Nom du magasin (ex. Carrefour)"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        box.addView(detected)
        box.addView(nameInput)

        AlertDialog.Builder(this)
            .setTitle("Ajouter la carte scannée")
            .setView(box)
            .setPositiveButton("Ajouter") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    cards.add(LoyaltyCard(name, number))
                    saveCards()
                    showCardsScreen()
                } else {
                    Toast.makeText(this, "Entre le nom du magasin", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showAddDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 12, 40, 0)
        }

        val nameInput = EditText(this).apply {
            hint = "Nom du magasin"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val numberInput = EditText(this).apply {
            hint = "Numéro / code de la carte"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        box.addView(nameInput)
        box.addView(numberInput)

        AlertDialog.Builder(this)
            .setTitle("Ajouter une carte")
            .setView(box)
            .setPositiveButton("Ajouter") { _, _ ->
                val name = nameInput.text.toString().trim()
                val number = numberInput.text.toString().trim()
                if (name.isNotEmpty() && number.isNotEmpty()) {
                    cards.add(LoyaltyCard(name, number))
                    saveCards()
                    showCardsScreen()
                } else {
                    Toast.makeText(this, "Nom et numéro obligatoires", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun refreshList() {
        listContainer.removeAllViews()

        if (cards.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Aucune carte.\nScanne ou ajoute ta première carte."
                textSize = 17f
                gravity = Gravity.CENTER
                setPadding(0, 70, 0, 20)
            }
            listContainer.addView(empty)
            return
        }

        cards.forEachIndexed { index, card ->
            val button = Button(this).apply {
                text = "💳  ${card.name}"
                textSize = 19f
                isAllCaps = false
                setPadding(20, 22, 20, 22)
                setOnClickListener { showCard(card) }
                setOnLongClickListener {
                    confirmDelete(index, card)
                    true
                }
            }
            listContainer.addView(button)
        }

        val hint = TextView(this).apply {
            text = "Appuie sur une carte pour afficher son code. Appui long pour la supprimer."
            textSize = 13f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }
        listContainer.addView(hint)
    }

    private fun showCard(card: LoyaltyCard) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 20, 32, 20)
        }

        val name = TextView(this).apply {
            text = card.name
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setPadding(0, 8, 0, 18)
        }

        val image = ImageView(this).apply {
            adjustViewBounds = true
            setImageBitmap(makeBarcode(card.number))
        }

        val number = TextView(this).apply {
            text = card.number
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setPadding(0, 18, 0, 10)
        }

        box.addView(name)
        box.addView(image, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            300
        ))
        box.addView(number)

        AlertDialog.Builder(this)
            .setView(box)
            .setPositiveButton("Fermer", null)
            .show()
    }

    private fun makeBarcode(value: String): Bitmap {
        val width = 1200
        val height = 420
        val matrix = MultiFormatWriter().encode(
            value,
            BarcodeFormat.CODE_128,
            width,
            height
        )

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun confirmDelete(index: Int, card: LoyaltyCard) {
        AlertDialog.Builder(this)
            .setTitle("Supprimer ${card.name} ?")
            .setMessage("La carte sera supprimée du portefeuille.")
            .setPositiveButton("Supprimer") { _, _ ->
                cards.removeAt(index)
                saveCards()
                showCardsScreen()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun saveCards() {
        val array = JSONArray()
        cards.forEach {
            array.put(JSONObject().apply {
                put("name", it.name)
                put("number", it.number)
            })
        }
        getSharedPreferences("portefeuille_carte", MODE_PRIVATE)
            .edit()
            .putString("cards", array.toString())
            .apply()
    }

    private fun loadCards() {
        val raw = getSharedPreferences("portefeuille_carte", MODE_PRIVATE)
            .getString("cards", "[]") ?: "[]"

        cards.clear()
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            cards.add(
                LoyaltyCard(
                    obj.optString("name"),
                    obj.optString("number")
                )
            )
        }
    }

    override fun onBackPressed() {
        showWalletHome()
    }
}
