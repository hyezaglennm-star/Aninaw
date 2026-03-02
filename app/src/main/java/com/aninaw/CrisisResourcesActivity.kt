package com.aninaw

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class CrisisResourcesActivity : AppCompatActivity() {

    data class CrisisResource(
        val name: String,
        val description: String,
        val phone: String? = null,
        val website: String? = null,
        val hours: String? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crisis_resources)

        findViewById<View>(R.id.incBackRhythm).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        com.aninaw.util.BackButton.bind(this)

        val container = findViewById<LinearLayout>(R.id.resourcesContainer)

        val resources = listOf(
            CrisisResource(
                name = "Emergency Services",
                description = "Immediate danger or life-threatening situation.",
                phone = "911",
                hours = "24/7"
            ),
            CrisisResource(
                name = "NCMH Crisis Hotline",
                description = "National Center for Mental Health Crisis Support",
                phone = "1553",
                website = "https://ncmh.gov.ph",
                hours = "Available 24/7"
            ),
            CrisisResource(
                name = "Department of Health (DOH)",
                description = "Official public health information",
                website = "https://doh.gov.ph"
            ),
            CrisisResource(
                name = "Department of Social Welfare and Development (DSWD)",
                description = "For abuse, domestic violence, trafficking, or unsafe home situations.",
                phone = "(02) 8931-8101",
                website = "https://ekwentomo.dswd.gov.ph/other-crisis-hotline/",
                hours = "Office hours may apply",


            )

        )

        resources.forEach { resource ->
            val card = layoutInflater.inflate(R.layout.item_crisis_resource, container, false)

            card.findViewById<TextView>(R.id.textName).text = resource.name
            card.findViewById<TextView>(R.id.textDescription).text = resource.description
            val textHours = card.findViewById<TextView>(R.id.textHours)

            if (resource.hours != null) {
                textHours.text = resource.hours
                textHours.visibility = View.VISIBLE
            } else {
                textHours.visibility = View.GONE
            }

            val btnCall = card.findViewById<MaterialButton>(R.id.btnCall)
            val btnLink = card.findViewById<MaterialButton>(R.id.btnLink)

            if (resource.phone != null) {
                btnCall.setOnClickListener {
                    startActivity(Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:${resource.phone}")
                    })
                }
            } else {
                btnCall.visibility = View.GONE
                btnLink.visibility = View.GONE
            }

            if (resource.website != null && resource.website.startsWith("http")) {

                btnLink.visibility = View.VISIBLE

                btnLink.setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resource.website)))
                }

            } else {
                btnLink.visibility = View.GONE
            }
            container.addView(card)
        }
    }
}