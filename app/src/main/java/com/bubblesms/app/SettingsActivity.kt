package com.bubblesms.app

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bubblesms.app.data.PrefsManager

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<Button>(R.id.editProfile).setOnClickListener { editProfile() }
        findViewById<Button>(R.id.changeStatus).setOnClickListener { chooseStatus() }
        findViewById<Button>(R.id.manageBlocked).setOnClickListener { blockedUsers() }
        findViewById<Button>(R.id.appPin).setOnClickListener { pinDialog() }
        findViewById<Button>(R.id.privacy).setOnClickListener {
            val now = !PrefsManager.isPrivateStatus(this)
            PrefsManager.setPrivateStatus(this, now)
            Toast.makeText(this, if(now) "Statut masqué" else "Statut visible", Toast.LENGTH_SHORT).show()
        }
    }
    private fun editProfile() {
        val input=EditText(this); input.setText(PrefsManager.getMyPseudo(this) ?: ""); input.hint="Ton pseudo"
        AlertDialog.Builder(this).setTitle("Modifier mon profil").setView(input).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer"){_,_->
            val p=input.text.toString().trim(); if(p.isNotEmpty()) PrefsManager.setMyPseudo(this,p)
        }.show()
    }
    private fun chooseStatus() {
        val values=arrayOf("En ligne","Occupé","Absent","Invisible")
        AlertDialog.Builder(this).setTitle("Mon statut").setSingleChoiceItems(values, values.indexOf(PrefsManager.getStatus(this))){d,w->PrefsManager.setStatus(this,values[w]);d.dismiss()}.show()
    }
    private fun blockedUsers() {
        val friends=PrefsManager.getFriends(this).filter{PrefsManager.isBlocked(this,it.phone)}
        val names=if(friends.isEmpty()) arrayOf("Aucun utilisateur bloqué") else friends.map{it.pseudo}.toTypedArray()
        AlertDialog.Builder(this).setTitle("Utilisateurs bloqués").setItems(names){_,which-> if(friends.isNotEmpty()){PrefsManager.setBlocked(this,friends[which].phone,false);Toast.makeText(this,"Débloqué",Toast.LENGTH_SHORT).show()}}.setPositiveButton("Fermer",null).show()
    }
    private fun pinDialog() {
        val input=EditText(this); input.inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; input.hint="4 à 8 chiffres"
        AlertDialog.Builder(this).setTitle("Code PIN de l'application").setMessage("Le PIN sera utilisé par l'écran de verrouillage BubbleSMS.").setView(input).setNegativeButton("Désactiver",{_,_->PrefsManager.setAppPin(this,null)}).setPositiveButton("Enregistrer"){_,_->
            val p=input.text.toString(); if(p.length in 4..8) PrefsManager.setAppPin(this,p) else Toast.makeText(this,"PIN invalide",Toast.LENGTH_SHORT).show()
        }.show()
    }
}
