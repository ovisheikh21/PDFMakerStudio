package com.pdfmakerstudio.app.data.templates

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import com.pdfmakerstudio.app.domain.model.RichFontFamily
import com.pdfmakerstudio.app.domain.model.RichPdfBlock
import com.pdfmakerstudio.app.domain.model.RichTextAlign
import java.io.File
import java.io.FileOutputStream

data class DocumentTemplate(
    val id: String,
    val titleBn: String,
    val titleEn: String,
    val subtitleBn: String,
    val subtitleEn: String,
    val docTitle: String,
    val includePassportPhotoSlot: Boolean = false,
    val blocks: List<RichPdfBlock>
)

object DocumentTemplates {

    fun createPassportPhotoPlaceholderFile(context: Context): Pair<String, Float>? {
        return runCatching {
            val w = 350
            val h = 450
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.rgb(248, 250, 252))

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                style = Paint.Style.STROKE
                strokeWidth = 5f
                pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
            }
            canvas.drawRect(10f, 10f, (w - 10).toFloat(), (h - 10).toFloat(), borderPaint)

            val silPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(203, 213, 225)
                style = Paint.Style.FILL
            }
            canvas.drawCircle(w / 2f, 148f, 52f, silPaint)
            canvas.drawArc(w / 2f - 96f, 219f, w / 2f + 96f, 411f, 180f, 180f, true, silPaint)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(51, 65, 85)
                textSize = 24f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            canvas.drawText("PASSPORT PHOTO", w / 2f, 368f, titlePaint)

            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105)
                textSize = 18f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.SANS_SERIF
            }
            canvas.drawText("35 x 45 mm", w / 2f, 398f, subPaint)

            val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(2, 132, 199)
                textSize = 16f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            canvas.drawText("Tap Replace for photo", w / 2f, 426f, hintPaint)

            val outDir = File(context.filesDir, "editor_overlays").apply { mkdirs() }
            val outFile = File(outDir, "passport_slot_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            bmp.recycle()
            outFile.absolutePath to (45f / 35f)
        }.getOrNull()
    }

    val letterTemplates: List<DocumentTemplate> = listOf(
        DocumentTemplate(
            id = "sick_leave",
            titleBn = "অসুস্থতার জন্য ছুটির আবেদন",
            titleEn = "Medical / Sick Leave Letter",
            subtitleBn = "অধ্যক্ষ / প্রধান শিক্ষক বরাবর পূর্ণাঙ্গ ও প্রফেশনাল আবেদনপত্র",
            subtitleEn = "Formal school/college sick leave application",
            docTitle = "অসুস্থতার কারণে ছুটির আবেদনপত্র",
            blocks = listOf(
                RichPdfBlock.TextBlock(
                    text = "তারিখ: [আজকের তারিখ লিখুন]\nবরাবর,\nঅধ্যক্ষ / প্রধান শিক্ষক\n[প্রতিষ্ঠানের নাম]\n[ঠিকানা]",
                    fontFamily = RichFontFamily.BENGALI_SANS,
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "বিষয়: অসুস্থতার কারণে তিন দিনের ছুটির জন্য আবেদন।",
                    fontFamily = RichFontFamily.BENGALI_SANS,
                    fontSizeSp = 14f,
                    isBold = true,
                    isUnderline = true,
                    colorHex = 0xFFB91C1CL,
                    leadingIcon = "📌"
                ),
                RichPdfBlock.TextBlock(
                    text = "মহোদয়,\n\nসবিনয় নিবেদন এই যে, আমি আপনার প্রতিষ্ঠানের [শ্রেণি/বিভাগ]-এর একজন নিয়মিত শিক্ষার্থী (রোল: [রোল])। গত [তারিখ] তারিখ থেকে আমি হঠাৎ তীব্র জ্বর ও শারীরিক দুর্বলতায় ভুগছি। চিকিৎসকের পরামর্শ অনুযায়ী আমাকে পূর্ণ বিশ্রামে থাকতে বলা হয়েছে, যার ফলে [তারিখ] থেকে [তারিখ] পর্যন্ত মোট ৩ (তিন) দিন ক্লাসে উপস্থিত থাকা সম্ভব হচ্ছে না।\n\nঅতএব, আমার অসুস্থতার বিষয়টি সদয় বিবেচনা করে উক্ত তিন দিনের ছুটি মঞ্জুর করলে চিরকৃতজ্ঞ থাকব।",
                    fontFamily = RichFontFamily.BENGALI_SERIF,
                    fontSizeSp = 14f
                ),
                RichPdfBlock.DividerBlock(),
                RichPdfBlock.TextBlock(
                    text = "নিবেদক,\n[আপনার নাম]\nশ্রেণি: [শ্রেণি] · রোল: [রোল]",
                    fontFamily = RichFontFamily.BENGALI_SANS,
                    fontSizeSp = 13f,
                    isBold = true
                )
            )
        ),
        DocumentTemplate(
            id = "office_leave",
            titleBn = "অফিস থেকে জরুরি ছুটির আবেদন",
            titleEn = "Corporate / Office Leave Request",
            subtitleBn = "ব্যবস্থাপক বা HR বিভাগ বরাবর পারিবারিক/ব্যক্তিগত ছুটির আবেদন",
            subtitleEn = "Formal Bengali office leave request",
            docTitle = "জরুরি ছুটির আবেদনপত্র (অফিসিয়াল)",
            blocks = listOf(
                RichPdfBlock.TextBlock(
                    text = "তারিখ: [তারিখ]\nবরাবর,\nমানবসম্পদ ব্যবস্থাপক / বিভাগীয় প্রধান\n[অফিস বা কোম্পানির নাম]\n[ঠিকানা]",
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "বিষয়: পারিবারিক জরুরি প্রয়োজনে নৈমিত্তিক ছুটির আবেদন।",
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFF1D4ED8L,
                    leadingIcon = "💼"
                ),
                RichPdfBlock.TextBlock(
                    text = "জনাব,\n\nযথাবিহিত সম্মানপূর্বক বিনীত নিবেদন এই যে, আমি [পদবি] হিসেবে আপনার প্রতিষ্ঠানে কর্মরত আছি। বিশেষ পারিবারিক জরুরি কাজের জন্য আগামী [শুরুর তারিখ] থেকে [শেষের তারিখ] পর্যন্ত মোট [দিন] দিন আমাকে কর্মস্থলের বাইরে অবস্থান করতে হবে। আমার অনুপস্থিতিতে চলমান কাজগুলো সহকর্মী [সহকর্মীর নাম]-এর নিকট বুঝিয়ে দেওয়া হয়েছে।\n\nঅনুগ্রহপূর্বক আমাকে উক্ত [দিন] দিনের ছুটি মঞ্জুর করে বাধিত করবেন।",
                    fontSizeSp = 14f
                ),
                RichPdfBlock.TextBlock(
                    text = "বিনীত,\n[আপনার নাম]\nপদবি: [পদবি] · আইডি: [ID]",
                    fontSizeSp = 13f,
                    isBold = true
                )
            )
        ),
        DocumentTemplate(
            id = "job_application",
            titleBn = "চাকরির আবেদনপত্র (Cover Letter)",
            titleEn = "Job Application & Cover Letter",
            subtitleBn = "যোগ্যতা, অভিজ্ঞতা ও সংযুক্তিসহ সম্পূর্ণ চাকরির দরখাস্ত",
            subtitleEn = "Complete Bengali cover letter format",
            docTitle = "চাকরির জন্য আবেদনপত্র",
            blocks = listOf(
                RichPdfBlock.TextBlock(
                    text = "তারিখ: [তারিখ]\nবরাবর,\nনিয়োগ কর্তৃপক্ষ / ব্যবস্থাপনা পরিচালক\n[প্রতিষ্ঠানের নাম]",
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "বিষয়: \"[পদের নাম]\" পদে নিয়োগের জন্য আবেদন।",
                    fontSizeSp = 14f,
                    isBold = true,
                    isUnderline = true,
                    colorHex = 0xFF047857L,
                    leadingIcon = "📄"
                ),
                RichPdfBlock.TextBlock(
                    text = "মহোদয়,\n\nপ্রকাশিত নিয়োগ বিজ্ঞপ্তি সূত্রে জানতে পারলাম যে, আপনার স্বনামধন্য প্রতিষ্ঠানে \"[পদের নাম]\" পদে জনবল নিয়োগ করা হবে। আমি উক্ত পদের জন্য একজন আগ্রহী ও যোগ্য প্রার্থী হিসেবে আমার জীবনবৃত্তান্ত (CV) পেশ করছি।\n\nআমি [প্রতিষ্ঠানের নাম] থেকে [ডিগ্রি] সম্পন্ন করেছি এবং [দক্ষতা/অভিজ্ঞতা] বিষয়ে বাস্তব কাজের অভিজ্ঞতা রয়েছে। সুযোগ পেলে আমি নিষ্ঠা ও সততার সাথে দায়িত্ব পালনে সচেষ্ট থাকব।\n\nসংযুক্তিসমূহ:\n১. পূর্ণাঙ্গ জীবনবৃত্তান্ত (CV)\n২. শিক্ষাগত সনদ ও এনআইডি ফটোকপি",
                    fontFamily = RichFontFamily.BENGALI_SERIF,
                    fontSizeSp = 14f
                ),
                RichPdfBlock.TextBlock(
                    text = "বিনীত নিবেদক,\n[আপনার পূর্ণ নাম]\nমোবাইল: [ফোন নম্বর]",
                    fontSizeSp = 13f,
                    isBold = true
                )
            )
        ),
        DocumentTemplate(
            id = "bank_service",
            titleBn = "ব্যাংক চেকবই / ATM কার্ড আবেদন",
            titleEn = "Bank Cheque / ATM Card Request",
            subtitleBn = "ব্যাংক শাখা ব্যবস্থাপক বরাবর ব্যাংকিং সেবার আবেদন",
            subtitleEn = "Official banking service application",
            docTitle = "ব্যাংকিং সেবার জন্য আবেদনপত্র",
            blocks = listOf(
                RichPdfBlock.TextBlock(
                    text = "তারিখ: [তারিখ]\nবরাবর,\nশাখা ব্যবস্থাপক\n[ব্যাংকের নাম], [শাখা]",
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "বিষয়: নতুন ডেবিট (ATM) কার্ড ও চেকবই ইস্যু করার আবেদন।",
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFF1D4ED8L,
                    leadingIcon = "◆"
                ),
                RichPdfBlock.TextBlock(
                    text = "মহোদয়,\n\nসবিনয় নিবেদন এই যে, আমি আপনার শাখার একজন নিয়মিত হিসাবধারী (হিসাব নং: [একাউন্ট নম্বর])। দৈনন্দিন লেনদেন সহজ করার জন্য আমার হিসাবের বিপরীতে একটি নতুন ডেবিট কার্ড ও চেকবই প্রয়োজন। অনুগ্রহপূর্বক প্রয়োজনীয় যাচাই শেষে দ্রুত কার্ড ও চেকবই প্রদানের ব্যবস্থা করলে বাধিত থাকব।",
                    fontSizeSp = 14f
                ),
                RichPdfBlock.TextBlock(
                    text = "বিনীত,\n[আপনার নাম] · মোবাইল: [ফোন]",
                    fontSizeSp = 13f,
                    isBold = true
                )
            )
        ),
        DocumentTemplate(
            id = "testimonial_tc",
            titleBn = "প্রশংসাপত্র / ছাড়পত্র (TC) আবেদন",
            titleEn = "Testimonial / TC Request",
            subtitleBn = "ভর্তি বা চাকরির কাজে প্রশংসাপত্র ও ছাড়পত্র পাওয়ার আবেদন",
            subtitleEn = "Student testimonial & transfer request",
            docTitle = "প্রশংসাপত্র / ছাড়পত্রের জন্য আবেদন",
            blocks = listOf(
                RichPdfBlock.TextBlock(
                    text = "তারিখ: [তারিখ]\nবরাবর,\nঅধ্যক্ষ / প্রধান শিক্ষক\n[প্রতিষ্ঠানের নাম]",
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "বিষয়: প্রশংসাপত্র (Testimonial) প্রদানের জন্য আবেদন।",
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFF6D28D9L,
                    leadingIcon = "🎓"
                ),
                RichPdfBlock.TextBlock(
                    text = "মহোদয়,\n\nবিনীত নিবেদন এই যে, আমি আপনার প্রতিষ্ঠান থেকে [সাল] সালে [পরীক্ষা] পরীক্ষায় জিপিএ [ফলাফল] অর্জন করে উত্তীর্ণ হয়েছি। উচ্চশিক্ষায় ভর্তির জন্য আপনার প্রতিষ্ঠান কর্তৃক প্রদত্ত একটি চারিত্রিক ও শিক্ষাগত প্রশংসাপত্র প্রয়োজন। অনুগ্রহপূর্বক আমাকে একটি প্রশংসাপত্র প্রদান করে বাধিত করবেন।",
                    fontFamily = RichFontFamily.BENGALI_SERIF,
                    fontSizeSp = 14f
                ),
                RichPdfBlock.TextBlock(
                    text = "বিনীত,\n[আপনার নাম] · রোল: [রোল]",
                    fontSizeSp = 13f,
                    isBold = true
                )
            )
        ),
        DocumentTemplate(
            id = "stipend_waiver",
            titleBn = "বিনা বেতনে অধ্যয়ন / বৃত্তির আবেদন",
            titleEn = "Tuition Waiver / Stipend Letter",
            subtitleBn = "দরিদ্র তহবিল থেকে বেতন মওকুফ ও বৃত্তি সহায়তার আবেদন",
            subtitleEn = "Scholarship & tuition waiver letter",
            docTitle = "বিনা বেতনে অধ্যয়নের জন্য আবেদন",
            blocks = listOf(
                RichPdfBlock.TextBlock(
                    text = "তারিখ: [তারিখ]\nবরাবর,\nঅধ্যক্ষ / প্রধান শিক্ষক\n[প্রতিষ্ঠানের নাম]",
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "বিষয়: বিনা বেতনে অধ্যয়ন ও আর্থিক সহায়তার আবেদন।",
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFFB45309L,
                    leadingIcon = "★"
                ),
                RichPdfBlock.TextBlock(
                    text = "মহোদয়,\n\nসবিনয় নিবেদন এই যে, আমি আপনার প্রতিষ্ঠানের [শ্রেণি] শ্রেণির একজন নিয়মিত শিক্ষার্থী। আমার পিতার সীমিত আয়ে পরিবারের ব্যয় নির্বাহের পর আমার পড়াশোনার খরচ চালানো কষ্টসাধ্য হয়ে পড়েছে। আমার পড়াশোনার প্রতি আগ্রহ ও বিগত পরীক্ষার ফলাফল বিবেচনা করে আমাকে বিনা বেতনে অধ্যয়নের সুযোগ প্রদান করলে কৃতজ্ঞ থাকব।",
                    fontFamily = RichFontFamily.BENGALI_SERIF,
                    fontSizeSp = 14f
                )
            )
        ),
        DocumentTemplate(
            id = "nid_correction",
            titleBn = "NID / জন্ম নিবন্ধন সংশোধনের আবেদন",
            titleEn = "NID / Certificate Correction Request",
            subtitleBn = "নাম বা জন্ম তারিখের ভুল তথ্য সংশোধনের আনুষ্ঠানিক আবেদন",
            subtitleEn = "Official document correction letter",
            docTitle = "তথ্য / বানান সংশোধনের জন্য আবেদনপত্র",
            blocks = listOf(
                RichPdfBlock.TextBlock(
                    text = "তারিখ: [তারিখ]\nবরাবর,\nসংশ্লিষ্ট কর্মকর্তা / রেজিস্ট্রার\n[কার্যালয়ের নাম]",
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "বিষয়: জাতীয় পরিচয়পত্র / জন্ম নিবন্ধনে ভুল তথ্য সংশোধনের আবেদন।",
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFFBE185DL,
                    leadingIcon = "📍"
                ),
                RichPdfBlock.TextBlock(
                    text = "মহোদয়,\n\nআমার [ডকুমেন্টের নাম ও নম্বর]-এ অনিচ্ছাকৃত ভুলের কারণে নামের বানান / জন্ম তারিখ ভুলভাবে লিপিবদ্ধ হয়েছে।\n• ভুল তথ্য: [ভুল তথ্য]\n• সঠিক তথ্য: [সঠিক তথ্য]\n\nপ্রমাণস্বরূপ প্রাসঙ্গিক সনদপত্র সংযুক্ত করা হলো। অনুগ্রহপূর্বক সংশোধিত কপি প্রদানের অনুরোধ করছি।",
                    fontSizeSp = 14f
                )
            )
        ),
        DocumentTemplate(
            id = "english_formal",
            titleBn = "English Formal Application Letter",
            titleEn = "Formal English Request / Leave Letter",
            subtitleBn = "ইংরেজিতে যেকোনো অফিশিয়াল বা একাডেমিক আবেদনপত্র",
            subtitleEn = "Executive English formal letter format",
            docTitle = "Formal Application Letter",
            blocks = listOf(
                RichPdfBlock.TextBlock(
                    text = "Date: [Insert Date]\n\nTo,\nThe Manager / Principal\n[Organization Name]\n[Address]",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "Subject: Formal Request for [Leave / Official Approval].",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 14f,
                    isBold = true,
                    isUnderline = true,
                    colorHex = 0xFF1D4ED8L,
                    leadingIcon = "✉"
                ),
                RichPdfBlock.TextBlock(
                    text = "Dear Sir / Madam,\n\nI am writing to formally request [briefly describe your request] due to [reason]. All ongoing tasks and responsibilities have been properly organized to ensure continuity.\n\nThank you very much for your time and kind consideration.\n\nSincerely,\n[Your Name]",
                    fontFamily = RichFontFamily.ENGLISH_SERIF,
                    fontSizeSp = 14f
                )
            )
        ),
        DocumentTemplate(
            id = "cv_fresh_graduate_bd",
            titleBn = "English CV 1 — স্ট্যান্ডার্ড ও ফ্রেশ গ্র্যাজুয়েট সিভি (পাসপোর্ট ছবিসহ)",
            titleEn = "English CV 1 — Standard Fresh Graduate CV (With Photo)",
            subtitleBn = "চাকরি ও যেকোনো প্রতিষ্ঠানের জন্য আদর্শ পূর্ণাঙ্গ জীবনবৃত্তান্ত (সব তথ্য এডিটযোগ্য)",
            subtitleEn = "Classic formal English CV with Career Objective, Education, Skills, Personal Info & Passport Photo box",
            docTitle = "CURRICULUM VITAE",
            includePassportPhotoSlot = true,
            blocks = listOf(
                RichPdfBlock.TextBlock(
                    text = "[YOUR FULL NAME IN CAPITALS]\nMobile: [+880 1XXX-XXXXXX]  |  Email: [your.email@example.com]\nPresent Address: [House/Road, Area, City - Postal Code]\nLinkedIn / Portfolio: [linkedin.com/in/yourprofile]",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f,
                    isBold = true
                ),
                RichPdfBlock.DividerBlock(colorHex = 0xFF0F172AL),
                RichPdfBlock.TextBlock(
                    text = "CAREER OBJECTIVE",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFF1D4ED8L,
                    leadingIcon = "★"
                ),
                RichPdfBlock.TextBlock(
                    text = "A motivated, hardworking, and detail-oriented graduate seeking an entry-level position in a reputable organization where I can apply my academic knowledge, analytical thinking, and communication skills to contribute effectively to institutional growth.",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "ACADEMIC QUALIFICATION",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFF1D4ED8L,
                    leadingIcon = "🎓"
                ),
                RichPdfBlock.TextBlock(
                    text = "1. Bachelor of [Honors / B.Sc. / B.B.A. / B.A.] — [Year]\n    Subject / Major: [Department Name]\n    Institution: [University / College Name]  |  Result: CGPA [X.XX / 4.00]\n\n2. Higher Secondary Certificate (HSC) — [Year]\n    Group: [Science / Business Studies / Humanities]\n    Institution: [College Name]  |  Board: [Board Name]  |  GPA: [X.XX / 5.00]\n\n3. Secondary School Certificate (SSC) — [Year]\n    Group: [Science / Business Studies / Humanities]\n    Institution: [School Name]  |  Board: [Board Name]  |  GPA: [X.XX / 5.00]",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "SKILLS & CORE COMPETENCIES",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFF1D4ED8L,
                    leadingIcon = "✓"
                ),
                RichPdfBlock.TextBlock(
                    text = "• Computer Literacy: MS Word, MS Excel, MS PowerPoint, Google Workspace, Typing & Email.\n• Communication: Strong presentation, teamwork, time management, and client coordination.\n• Language Proficiency: Fluent in Bengali (Native) and English (Reading, Writing & Speaking).",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "PERSONAL INFORMATION",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFF1D4ED8L,
                    leadingIcon = "📄"
                ),
                RichPdfBlock.TextBlock(
                    text = "Father's Name       : [Father's Full Name]\nMother's Name     : [Mother's Full Name]\nDate of Birth         : [DD Month YYYY]\nGender / Marital     : [Male / Female]  |  [Single / Married]\nNationality / NID    : [Your Nationality]  |  NID: [National ID Number]\nPermanent Address: [Village/Road, Post Office, Upazila, District]",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "REFERENCES & DECLARATION",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFF1D4ED8L,
                    leadingIcon = "💼"
                ),
                RichPdfBlock.TextBlock(
                    text = "1. [Referee Name 1], [Designation & Organization], Phone: [Phone], Email: [Email]\n2. [Referee Name 2], [Designation & Organization], Phone: [Phone], Email: [Email]\n\nDeclaration: I hereby declare that all the information stated above is true and accurate to the best of my knowledge and belief.\n\nDate: [Insert Date]                                                     Signature: _______________________",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f
                )
            )
        ),
        DocumentTemplate(
            id = "cv_corporate_executive_bd",
            titleBn = "English CV 2 — কর্পোরেট, ব্যাংক ও অভিজ্ঞ চাকরি সিভি (পাসপোর্ট ছবিসহ)",
            titleEn = "English CV 2 — Corporate, Banking & Executive CV",
            subtitleBn = "অভিজ্ঞতা, দায়িত্ব, ট্রেনিং ও কর্পোরেট প্রোফাইলসহ ব্যাংক ও প্রাইভেট চাকরির ফরম্যাট",
            subtitleEn = "Formal executive CV tailored for corporate, banking, NGO & management positions",
            docTitle = "CURRICULUM VITAE",
            includePassportPhotoSlot = true,
            blocks = listOf(
                RichPdfBlock.TextBlock(
                    text = "[YOUR FULL NAME]\n[Senior Officer / Executive — Operations & Management]\nPhone: [+880 1XXX-XXXXXX]  |  Email: [name@company.com]\nAddress: [Street, Area, City - Postal Code]",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f,
                    isBold = true
                ),
                RichPdfBlock.DividerBlock(colorHex = 0xFF1D4ED8L),
                RichPdfBlock.TextBlock(
                    text = "EXECUTIVE SUMMARY",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFF047857L,
                    leadingIcon = "💼"
                ),
                RichPdfBlock.TextBlock(
                    text = "Result-driven professional with [X+] years of progressive experience in corporate operations, client relationship management, team supervision, and administrative compliance. Proven track record of meeting organizational targets with accuracy and integrity.",
                    fontFamily = RichFontFamily.ENGLISH_SERIF,
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "PROFESSIONAL EXPERIENCE",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFF047857L,
                    leadingIcon = "★"
                ),
                RichPdfBlock.TextBlock(
                    text = "[Current / Last Job Title] — [Company / Bank Name]\nDuration: [Month Year] – [Present / Month Year]  |  Location: [City]\n• Managed daily branch/department operations, client onboarding, and KPI reporting.\n• Coordinated cross-functional teams to achieve [XX%] improvement in turnaround efficiency.\n• Prepared monthly executive summaries, compliance audits, and vendor documentation.\n\n[Previous Job Title] — [Previous Organization Name]\nDuration: [Month Year] – [Month Year]  |  Location: [City]\n• Handled customer service inquiries, database records, and administrative scheduling.\n• Supported senior management with correspondence, documentation, and billing workflows.",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "EDUCATION & PROFESSIONAL TRAINING",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFF047857L,
                    leadingIcon = "🎓"
                ),
                RichPdfBlock.TextBlock(
                    text = "• Master of Business Administration (MBA) / Masters — [Major], [University Name] ([Year]) — CGPA: [X.XX]\n• Bachelor Degree (BBA / B.Com / B.A.) — [University / College Name] ([Year]) — CGPA: [X.XX]\n• HSC ([Year], GPA [X.XX]) & SSC ([Year], GPA [X.XX])\n• Professional Training: [Certificate in Banking Operations / Office Management / Leadership, Institute Name]",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "PERSONAL DETAILS & REFERENCES",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFF047857L,
                    leadingIcon = "📄"
                ),
                RichPdfBlock.TextBlock(
                    text = "Father's Name: [Father's Name]  |  Mother's Name: [Mother's Name]\nDate of Birth: [DD/MM/YYYY]  |  Nationality: [Nationality]  |  Languages: Bengali, English\nReferees: 1) [Manager Name, Designation, Organization, Phone]  2) [Academic/Colleague Referee, Phone]\n\nSignature: _______________________                      Date: [DD/MM/YYYY]",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f
                )
            )
        ),
        DocumentTemplate(
            id = "cv_it_technical_bd",
            titleBn = "English CV 3 — আইটি, সফটওয়্যার ও টেকনিক্যাল সিভি (পাসপোর্ট ছবিসহ)",
            titleEn = "English CV 3 — IT, Software & Engineering CV",
            subtitleBn = "প্রোগ্রামিং স্কিল, প্রজেক্ট, GitHub/পোর্টফোলিও ও টেকনিক্যাল প্রোফাইলের আধুনিক ফরম্যাট",
            subtitleEn = "Structured technical resume for Software Developers, IT Officers & Engineers",
            docTitle = "CURRICULUM VITAE",
            includePassportPhotoSlot = true,
            blocks = listOf(
                RichPdfBlock.TextBlock(
                    text = "[YOUR FULL NAME]\n[Software Engineer / IT Support Specialist / Web Developer]\nMobile: [+880 1XXX-XXXXXX]  |  Email: [dev.email@example.com]\nGitHub: [github.com/username]  |  Location: [City]",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f,
                    isBold = true
                ),
                RichPdfBlock.DividerBlock(colorHex = 0xFF0284C7L),
                RichPdfBlock.TextBlock(
                    text = "TECHNICAL SKILLS & TOOLS",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFF1D4ED8L,
                    leadingIcon = "✦"
                ),
                RichPdfBlock.TextBlock(
                    text = "• Programming & Web: [JavaScript / TypeScript, Python, Java, PHP, HTML5, CSS3]\n• Frameworks & Databases: [React / Next.js / Laravel / Node.js, MySQL, PostgreSQL, Firebase]\n• IT & Networking: [Windows/Linux Administration, Hardware Troubleshooting, LAN/MikroTik, Git]\n• Productivity Tools: [MS Office Suite, Figma, VS Code, Jira, Google Cloud / Hosting]",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "EXPERIENCE & KEY PROJECTS",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFF1D4ED8L,
                    leadingIcon = "💼"
                ),
                RichPdfBlock.TextBlock(
                    text = "[Junior Software Developer / IT Executive] — [Company Name] ([Month Year – Present])\n• Built and maintained web/mobile applications and managed internal office IT infrastructure.\n• Reduced system downtime and improved database query performance across reporting dashboards.\n\nKey Project 1: [E-Commerce / Inventory Management System]\n• Designed full-stack CRUD modules, authentication, invoice PDF generation, and admin controls.\n\nKey Project 2: [Portfolio / Institutional Management Portal]\n• Implemented responsive UI, role-based access, and automated backup workflows.",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "EDUCATION, PERSONAL INFO & REFERENCES",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFF1D4ED8L,
                    leadingIcon = "🎓"
                ),
                RichPdfBlock.TextBlock(
                    text = "• B.Sc. in [Computer Science & Engineering / IT / Diploma in CSE] — [University/Institute] ([Year]) — CGPA: [X.XX]\n• HSC ([Year], GPA [X.XX]) & SSC ([Year], GPA [X.XX])\n• Father's Name: [Name]  |  Date of Birth: [DD Month YYYY]  |  Nationality: [Nationality]\n• Languages: Bengali & English  |  References: Available upon request\n\nSignature: _______________________                      Date: [Insert Date]",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f
                )
            )
        ),
        DocumentTemplate(
            id = "cv_accounts_admin_bd",
            titleBn = "English CV 4 — অফিস অ্যাডমিন, অ্যাকাউন্টস ও ফাইন্যান্স সিভি (পাসপোর্ট ছবিসহ)",
            titleEn = "English CV 4 — Office Admin, Accounts & Finance CV",
            subtitleBn = "হিসাবরক্ষণ, অফিস ম্যানেজমেন্ট, Excel, Tally/ERP ও ভ্যাট-ট্যাক্স কাজের প্রফেশনাল সিভি",
            subtitleEn = "Designed for Accounts Officer, Admin Executive, Commercial & Store roles",
            docTitle = "CURRICULUM VITAE",
            includePassportPhotoSlot = true,
            blocks = listOf(
                RichPdfBlock.TextBlock(
                    text = "[YOUR FULL NAME]\n[Accounts & Admin Executive / Officer]\nPhone: [+880 1XXX-XXXXXX]  |  Email: [accounts.name@example.com]\nPresent Address: [House/Road, Area, City - Postal Code]",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f,
                    isBold = true
                ),
                RichPdfBlock.DividerBlock(colorHex = 0xFFB45309L),
                RichPdfBlock.TextBlock(
                    text = "CAREER OBJECTIVE & ACCOUNTING EXPERTISE",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFFB45309L,
                    leadingIcon = "💼"
                ),
                RichPdfBlock.TextBlock(
                    text = "Dedicated and detail-focused Accounts & Admin professional seeking a responsible position to utilize my expertise in financial bookkeeping, voucher processing, inventory control, payroll, and regulatory compliance.\n\n• Accounting & Software: Tally ERP, QuickBooks, Advanced MS Excel (VLOOKUP, Pivot Table), MS Word.\n• Finance Tasks: Daily cashbook, bank reconciliation, petty cash, billing/invoicing, VAT & Tax preparation.\n• Administration: HR attendance tracking, official correspondence, filing, and procurement support.",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "WORK EXPERIENCE",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFFB45309L,
                    leadingIcon = "✓"
                ),
                RichPdfBlock.TextBlock(
                    text = "[Accounts & Admin Officer] — [Company / Trading / Garments / Firm Name] ([Year – Present])\n• Maintained daily ledger entries, supplier payments, receivables tracking, and bank reconciliation.\n• Prepared monthly salary sheets, overtime calculations, and audit-ready expense reports.\n• Coordinated office supplies, vendor invoices, and official documentation smoothly.",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "ACADEMIC QUALIFICATION & PERSONAL PROFILE",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFFB45309L,
                    leadingIcon = "🎓"
                ),
                RichPdfBlock.TextBlock(
                    text = "• Master of Business Studies (MBS / M.Com) in [Accounting / Finance] — [College/University] ([Year])\n• Bachelor of Business Studies (BBS / BBA) — [College/University] ([Year]) — Result: [CGPA / Class]\n• HSC (Business Studies, [Year]) & SSC (Business Studies, [Year])\n\nFather's Name: [Father's Name]  |  Mother's Name: [Mother's Name]\nDate of Birth: [DD Month YYYY]  |  NID: [NID Number]  |  Nationality: [Nationality]\nPermanent Address: [Village/Road, Upazila, District]\n\nReferences: 1) [Name, Designation, Phone]  2) [Name, Designation, Phone]\nSignature: _______________________                      Date: [Insert Date]",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f
                )
            )
        ),
        DocumentTemplate(
            id = "cv_sales_marketing_bd",
            titleBn = "English CV 5 — সেলস, মার্কেটিং ও ফিল্ড অফিসার সিভি (পাসপোর্ট ছবিসহ)",
            titleEn = "English CV 5 — Sales, Marketing & Field Executive CV",
            subtitleBn = "মেডিকেল/ফার্মা/FMCG মার্কেটিং, ডিলার ম্যানেজমেন্ট ও সেলস টার্গেট ভিত্তিক প্রফেশনাল সিভি",
            subtitleEn = "Clean achievement-oriented CV for Sales Representative, Marketing & Territory Officer roles",
            docTitle = "CURRICULUM VITAE",
            includePassportPhotoSlot = true,
            blocks = listOf(
                RichPdfBlock.TextBlock(
                    text = "[YOUR FULL NAME]\n[Sales & Marketing Officer / Territory Executive]\nMobile: [+880 1XXX-XXXXXX]  |  Email: [sales.profile@example.com]\nAddress: [Area, Thana, District - Postal Code]",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f,
                    isBold = true
                ),
                RichPdfBlock.DividerBlock(colorHex = 0xFFBE185DL),
                RichPdfBlock.TextBlock(
                    text = "CAREER PROFILE & KEY STRENGTHS",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFFBE185DL,
                    leadingIcon = "➜"
                ),
                RichPdfBlock.TextBlock(
                    text = "Energetic and target-oriented Sales & Marketing professional with strong field communication, distributor handling, and customer retention skills. Capable of expanding market coverage, executing promotional campaigns, and achieving monthly sales targets under pressure.\n\n• Field & Sales Skills: Territory coverage, client pitching, dealer coordination, order collection & recovery.\n• Tools & Mobility: MS Office, Reporting Apps, Smartphone Sales Tracking, Valid Driving License (Optional).",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "EXPERIENCE & ACHIEVEMENTS",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFFBE185DL,
                    leadingIcon = "★"
                ),
                RichPdfBlock.TextBlock(
                    text = "[Sales Officer / Medical Promotion Officer] — [Company Name] ([Month Year – Present])\n• Consistently achieved [100%+] of monthly territory sales and collection quotas.\n• Built strong relationships with retail outlets, wholesalers, and key institutional buyers.\n• Conducted regular market surveys and competitor price/activity reporting for regional managers.",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f
                ),
                RichPdfBlock.TextBlock(
                    text = "EDUCATION, PERSONAL INFO & DECLARATION",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 14f,
                    isBold = true,
                    colorHex = 0xFFBE185DL,
                    leadingIcon = "🎓"
                ),
                RichPdfBlock.TextBlock(
                    text = "• Graduation (Honors / Pass / BBA / B.Sc.) — [Institution Name] ([Year]) — Result: [CGPA]\n• HSC ([Year], Board: [Board], GPA: [X.XX]) & SSC ([Year], Board: [Board], GPA: [X.XX])\n\nFather's Name: [Father's Name]  |  Date of Birth: [DD Month YYYY]  |  Height: [X'X\"]\nNationality: [Nationality]  |  Marital Status: [Single / Married]  |  Blood Group: [O+ / A+ / B+]\nPresent & Permanent Address: [Full Editable Address]\n\nDeclaration: I certify that the above particulars are true to the best of my knowledge.\nSignature: _______________________                      Date: [Insert Date]",
                    fontFamily = RichFontFamily.ENGLISH_MODERN,
                    fontSizeSp = 13f
                )
            )
        )
    )

    val notesTemplates: List<DocumentTemplate> = listOf(
        DocumentTemplate(
            id = "notes_lecture",
            titleBn = "ক্লাস লেকচার ও পরীক্ষার নোট",
            titleEn = "Study & Exam Notes",
            subtitleBn = "অধ্যায়, মূল ধারণা, উদাহরণ ও সারসংক্ষেপসহ পরিপাটি শিট",
            subtitleEn = "Structured chapter concepts & examples",
            docTitle = "অধ্যায় ভিত্তিক ক্লাস নোট ও সারসংক্ষেপ",
            blocks = listOf(
                RichPdfBlock.TextBlock(
                    text = "বিষয়: [বিষয়ের নাম]  |  অধ্যায়: [অধ্যায়]  |  তারিখ: [তারিখ]",
                    fontSizeSp = 12f,
                    isBold = true,
                    colorHex = 0xFF475569L,
                    leadingIcon = "📅"
                ),
                RichPdfBlock.DividerBlock(),
                RichPdfBlock.TextBlock(
                    text = "১. মূল ধারণা ও সংজ্ঞা",
                    fontSizeSp = 16f,
                    isBold = true,
                    colorHex = 0xFF1D4ED8L,
                    leadingIcon = "🎓"
                ),
                RichPdfBlock.TextBlock(
                    text = "• প্রথম গুরুত্বপূর্ণ সংজ্ঞা বা সূত্র এখানে লিখুন।\n• দ্বিতীয় মূল তত্ত্ব ও ব্যাখ্যা সংযুক্ত করুন।\n• পরীক্ষার জন্য বিশেষ রিভিশন পয়েন্ট এখানে রাখুন।",
                    fontSizeSp = 14f
                )
            )
        ),
        DocumentTemplate(
            id = "notes_meeting",
            titleBn = "মিটিং মিনিটস ও কাজের পরিকল্পনা",
            titleEn = "Meeting Minutes & Action Plan",
            subtitleBn = "অফিস মিটিং, সিদ্ধান্ত ও দায়িত্ব বণ্টনের তালিকা",
            subtitleEn = "Meeting agenda, decisions & action list",
            docTitle = "মিটিং মিনিটস ও কাজের রূপরেখা",
            blocks = listOf(
                RichPdfBlock.TextBlock(
                    text = "প্রজেক্ট: [নাম] · তারিখ: [তারিখ]",
                    fontSizeSp = 12f,
                    isBold = true,
                    colorHex = 0xFF475569L,
                    leadingIcon = "💼"
                ),
                RichPdfBlock.TextBlock(
                    text = "গৃহীত প্রধান সিদ্ধান্তসমূহ",
                    fontSizeSp = 16f,
                    isBold = true,
                    colorHex = 0xFFB91C1CL,
                    leadingIcon = "★"
                ),
                RichPdfBlock.TextBlock(
                    text = "১. [সিদ্ধান্ত ১ ও সময়সীমা]\n২. [সিদ্ধান্ত ২ ও দায়িত্বপ্রাপ্ত ব্যক্তি]\n৩. [পরবর্তী মিটিংয়ের তারিখ]",
                    fontSizeSp = 14f
                )
            )
        ),
        DocumentTemplate(
            id = "notes_assignment",
            titleBn = "অ্যাসাইনমেন্ট ও প্রজেক্ট রিপোর্ট",
            titleEn = "Assignment & Project Report",
            subtitleBn = "ভূমিকা, মূল আলোচনা ও উপসংহারসহ ফরম্যাট",
            subtitleEn = "Academic assignment outline",
            docTitle = "অ্যাসাইনমেন্ট / প্রজেক্ট প্রতিবেদন",
            blocks = listOf(
                RichPdfBlock.TextBlock(
                    text = "প্রস্তুতকারক: [আপনার নাম] · আইডি: [ID] · তারিখ: [তারিখ]",
                    fontSizeSp = 13f,
                    isBold = true,
                    align = RichTextAlign.CENTER
                ),
                RichPdfBlock.DividerBlock(),
                RichPdfBlock.TextBlock(
                    text = "ভূমিকা ও মূল বিশ্লেষণ",
                    fontSizeSp = 16f,
                    isBold = true,
                    colorHex = 0xFF047857L,
                    leadingIcon = "📄"
                ),
                RichPdfBlock.TextBlock(
                    text = "[প্রতিবেদনের বিস্তারিত তথ্য ও পয়েন্টসমূহ এখানে লিখুন। উপরের ছবি আইকন থেকে প্রয়োজনীয় ডায়াগ্রাম বা ছবি যুক্ত করতে পারবেন।]",
                    fontFamily = RichFontFamily.BENGALI_SERIF,
                    fontSizeSp = 14f
                )
            )
        ),
        DocumentTemplate(
            id = "notes_expense",
            titleBn = "দৈনন্দিন হিসাব ও কাজের তালিকা",
            titleEn = "Daily Expense & Checklist",
            subtitleBn = "তারিখ অনুযায়ী আয়-ব্যয় ও বাজার বা কাজের নোট",
            subtitleEn = "Clean expense log & daily checklist",
            docTitle = "দৈনন্দিন হিসাব ও নোটবুক",
            blocks = listOf(
                RichPdfBlock.TextBlock(
                    text = "মাস / তারিখ: [তারিখ] · হিসাবের খাত: [ব্যক্তিগত / অফিস]",
                    fontSizeSp = 13f,
                    isBold = true,
                    colorHex = 0xFF475569L,
                    leadingIcon = "📅"
                ),
                RichPdfBlock.TextBlock(
                    text = "০১. [বিবরণ ১] .................. ১,২০০ টাকা\n০২. [বিবরণ ২] .................. ৪৫০ টাকা\n০৩. [বিবরণ ৩] .................. ২,৩৫০ টাকা\n\nমোট ব্যয়: ৪,০০০ টাকা",
                    fontSizeSp = 14f
                )
            )
        )
    )
}
