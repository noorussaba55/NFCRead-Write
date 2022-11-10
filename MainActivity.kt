package com.example.nfc_read_write

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.*
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcV
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import kotlin.experimental.and

class MainActivity : AppCompatActivity() {
    lateinit var writeTagFilters: Array<IntentFilter>
    private lateinit var tvNFCContent: TextView
    private lateinit var message: TextView
    private lateinit var btnWrite: Button
    var nfcAdapter: NfcAdapter? = null
    var pendingIntent: PendingIntent? = null
    var writeMode = false
    var myTag: Tag? = null

    public override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        tvNFCContent = findViewById<TextView>(R.id.nfc_contents)
        message = findViewById<EditText>(R.id.edit_message)
        btnWrite = findViewById<Button>(R.id.button_write_nfc)

        btnWrite.setOnClickListener {
            try {
                if (myTag == null) {
                    Toast.makeText(this, ERROR_DETECTED, Toast.LENGTH_LONG).show()
                } else {
                    write(message.text.toString(), myTag)
                    Toast.makeText(this, WRITE_SUCCESS, Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                Toast.makeText(this, WRITE_ERROR, Toast.LENGTH_LONG).show()
                e.printStackTrace()
            } catch (e: FormatException) {
                Toast.makeText(this, WRITE_ERROR, Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            // Stop here, we definitely need NFC
            Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show()
            finish()
        }

        //For when the activity is launched by the intent-filter for android.nfc.action.NDEF_DISCOVERE
        readFromIntent(intent)
        pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            0
        )
        val tagDetected = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT)
        writeTagFilters = arrayOf(tagDetected)
    }

    /******************************************************************************
     * Read From NFC Tag
     ****************************************************************************/

    private fun readFromIntent(intent: Intent) {

        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action || NfcAdapter.ACTION_TECH_DISCOVERED == intent.action || NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            myTag = intent.getParcelableExtra<Parcelable>(NfcAdapter.EXTRA_TAG) as Tag?
//
//            val read_cmd = byteArrayOf(
//                0x00.toByte(), // Flags
//                0x23.toByte(), // Command: Read multiple blocks
//                0x00.toByte(), // First block (offset)
//                0x04.toByte()  // Number of blocks
//            )
            for (tech:String in myTag?.techList!!) {

                if (tech.equals("android.nfc.tech.NfcV")) {
                    val nfcVTag = NfcV.get(myTag);

                    try {

                        nfcVTag.connect();
                        Toast.makeText(
                            getApplicationContext(),
                            "HELLO NFC",
                            Toast.LENGTH_SHORT
                        ).show();
                    } catch (exception: IOException) {
                        Toast.makeText(
                            getApplicationContext(),
                            "Could not open a connection!",
                            Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    val isConnected = nfcVTag.isConnected

                    if (isConnected){
                        try {

                            val id: ByteArray = myTag!!.id
                            val BLOCKS_TO_READ = 0x10 // 16 blocks + 1 by default sent
                            val cmd = byteArrayOf(
                                0x20.toByte(), // Flags 60
                                0x23.toByte(), // Read multiple blocks
                                0x00.toByte(),
                                0x00.toByte(),
                                0x00.toByte(),
                                0x00.toByte(),
                                0x00.toByte(),
                                0x00.toByte(),
                                0x00.toByte(),
                                0x00.toByte(),
                                0x00.toByte(), // Starting address
                                BLOCKS_TO_READ.toByte()  // Number of blocks to be read from the NFC Tag
                            )
                            System.arraycopy(id, 0, cmd, 2, 8)
                            val max = nfcVTag.maxTransceiveLength
                            val userdata = nfcVTag.transceive(cmd); //receiving an extra byte 0 at the start, ignore that when decoding
                            if (userdata != null) {
                                val msgs = userdata.toHexString()
                                tvNFCContent.text = "Text read from NFC = $msgs"
                                nfcVTag.close()
                            }

                        } catch (exception: IOException) {
                            Toast.makeText(
                                getApplicationContext(),
                                "An error occurred while reading!",
                                Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }
                    }
                    else{
                        Toast.makeText(
                            getApplicationContext(),
                            "not connected",
                            Toast.LENGTH_SHORT
                        ).show();
                    }
                }
            }
        }
    }

    @JvmOverloads
    fun ByteArray.toHexString(separator: CharSequence = " ",  prefix: CharSequence = "[",  postfix: CharSequence = "]") =
        this.joinToString(separator, prefix, postfix) {
            String.format("0x%02X", it)
        }

//Method only reading from NDEF which is not available for our nfc tags
//    private fun readFromIntent(intent: Intent) {
//        val action = intent.action
//        if (NfcAdapter.ACTION_TAG_DISCOVERED == action || NfcAdapter.ACTION_TECH_DISCOVERED == action || NfcAdapter.ACTION_NDEF_DISCOVERED == action) {
//            myTag = intent.getParcelableExtra<Parcelable>(NfcAdapter.EXTRA_TAG) as Tag?
//            val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
//            var msgs = mutableListOf<NdefMessage>()
//            if (rawMsgs != null) {
//                for (i in rawMsgs.indices) {
//                    msgs.add(i, rawMsgs[i] as NdefMessage)
//                }
//                buildTagViews(msgs.toTypedArray())
//            }
//        }
//    }

    private fun buildTagViews(msgs: Array<NdefMessage>) {
        if (msgs == null || msgs.isEmpty()) return
        var text = ""
        val payload = msgs[0].records[0].payload
        val textEncoding: Charset = if ((payload[0] and 128.toByte()).toInt() == 0) Charsets.UTF_8 else Charsets.UTF_16 // Get the Text Encoding
        val languageCodeLength: Int = (payload[0] and 51).toInt() // Get the Language Code, e.g. "en"
        try {
            // Get the Text
            text = String(
                payload,
                languageCodeLength + 1,
                payload.size - languageCodeLength - 1,
                textEncoding
            )
        } catch (e: UnsupportedEncodingException) {
            Log.e("UnsupportedEncoding", e.toString())
        }
        tvNFCContent.text = "Message read from NFC Tag:\n $text"
    }

    /******************************************************************************
     * Write to NFC Tag
     ****************************************************************************/
    @Throws(IOException::class, FormatException::class)
    private fun write(msg: String, tag: Tag?): Boolean {
        val nfcRecord = createRecord(msg)
        val text = NdefMessage(arrayOf(nfcRecord))

        try {
//            val nDefTag = Ndef.get(tag)
//
//            nDefTag?.let {
//                it.connect()
//                if (it.maxSize < text.toByteArray().size) {
//                    //Message to large to write to NFC tag
//                    return false
//                }
//                if (it.isWritable) {
//                    it.writeNdefMessage(text)
//                    it.close()
//                    //Message is written to tag
//                    return true
//                } else {
//                    //NFC tag is read-only
//                    return false
//                }
//            }

            val nDefFormatableTag = NdefFormatable.get(tag)

            nDefFormatableTag?.let {
                try {
                    it.connect()
                    it.format(text)
                    it.close()
                    //The data is written to the tag
                    return true
                } catch (e: IOException) {
                    //Failed to format tag
                    return false
                }
            }
            //NDEF is not supported
            return false

        } catch (e: Exception) {
            //Write operation has failed
        }
        return false
    }

//    private fun write(text: String, tag: Tag?) {
//        val records = arrayOf(createRecord(text))
//        val message = NdefMessage(records)
//        // Get an instance of Ndef for the tag.
//        val ndef = Ndef.get(tag)
//        // Enable I/O
//        ndef.connect()
//        // Write the message
//        ndef.writeNdefMessage(message)
//        // Close the connection
//        ndef.close()
//    }

    @Throws(UnsupportedEncodingException::class)
    private fun createRecord(text: String): NdefRecord {
        val lang = "en"
        val textBytes = text.toByteArray()
        val langBytes = lang.toByteArray(charset("US-ASCII"))
        val langLength = langBytes.size
        val textLength = textBytes.size
        val payload = ByteArray(1 + langLength + textLength)

        // set status byte (see NDEF spec for actual bits)
        payload[0] = langLength.toByte()

        // copy langbytes and textbytes into payload
        System.arraycopy(langBytes, 0, payload, 1, langLength)
        System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength)
        return NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, ByteArray(0), payload)
    }

    /**
     * For reading the NFC when the app is already launched
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readFromIntent(intent)
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            myTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
    }

    public override fun onPause() {
        super.onPause()
        WriteModeOff()
    }

    public override fun onResume() {
        super.onResume()
        WriteModeOn()
    }

    /******************************************************************************
     * Enable Write and foreground dispatch to prevent intent-filter to launch the app again
     ****************************************************************************/
    private fun WriteModeOn() {
        writeMode = true
        nfcAdapter!!.enableForegroundDispatch(this, pendingIntent, writeTagFilters, null)
    }

    /******************************************************************************
     * Disable Write and foreground dispatch to allow intent-filter to launch the app
     ****************************************************************************/
    private fun WriteModeOff() {
        writeMode = false
        nfcAdapter!!.disableForegroundDispatch(this)
    }

    companion object {
        const val ERROR_DETECTED = "No NFC tag detected!"
        const val WRITE_SUCCESS = "Text written to the NFC tag successfully!"
        const val WRITE_ERROR = "Error during writing, is the NFC tag close enough to your device?"
    }
}