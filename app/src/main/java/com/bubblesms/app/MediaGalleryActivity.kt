package com.bubblesms.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bubblesms.app.data.PrefsManager
import java.io.File

class MediaGalleryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_gallery)
        val phone=intent.getStringExtra("phone") ?: return
        findViewById<TextView>(R.id.galleryTitle).text="Médias • ${intent.getStringExtra("pseudo") ?: phone}"
        val box=findViewById<LinearLayout>(R.id.galleryList)
        val media=PrefsManager.getMessages(this,phone).filter{it.attachmentUri.isNotBlank()}
        if(media.isEmpty()){ findViewById<TextView>(R.id.emptyGallery).visibility=android.view.View.VISIBLE; return }
        media.forEach { m ->
            val b=Button(this); b.text="${if(m.attachmentType.startsWith("image")) "🖼️" else if(m.attachmentType.startsWith("video")) "🎬" else "📎"}  ${File(Uri.parse(m.attachmentUri).path ?: "").name}"
            b.setOnClickListener { try { val u=Uri.parse(m.attachmentUri); startActivity(Intent(Intent.ACTION_VIEW).apply{setDataAndType(u,m.attachmentType.ifBlank{"*/*"});addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}) } catch(_:Exception){} }
            box.addView(b)
        }
    }
}
