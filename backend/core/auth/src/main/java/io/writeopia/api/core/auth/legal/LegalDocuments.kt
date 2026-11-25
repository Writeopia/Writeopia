package io.writeopia.api.core.auth.legal

import io.writeopia.sdk.serialization.response.LegalDocumentResponse
import io.writeopia.sdk.serialization.response.LegalSectionResponse

/**
 * The legal text shown when signing up. Bump [VERSION] and [EFFECTIVE_DATE] whenever a document
 * changes in a way users must accept again: the version is also the ETag of the cached response.
 */
object LegalDocuments {
    const val VERSION = "2026-11-12"
    const val EFFECTIVE_DATE = "2026-11-12"
    const val CONTACT = "hi@writeopia.io"

    const val TERMS = "terms"
    const val PRIVACY = "privacy"

    fun get(type: String): LegalDocumentResponse? = when (type) {
        TERMS -> terms
        PRIVACY -> privacy
        else -> null
    }

    val terms = LegalDocumentResponse(
        type = TERMS,
        version = VERSION,
        effectiveDate = EFFECTIVE_DATE,
        title = "Terms of Service",
        sections = listOf(
            LegalSectionResponse(
                "1. Agreement",
                "These Terms of Service (\"Terms\") are a contract between you and Writeopia " +
                    "(\"Writeopia\", \"we\", \"us\"). They apply to the Writeopia apps, website and " +
                    "services (the \"Service\"). By creating an account or using the Service you agree to " +
                    "these Terms and to our Privacy Policy. If you use the Service for an organization, you " +
                    "confirm that you can bind it to these Terms.\n\n" +
                    "If you do not agree, do not create an account or use the Service."
            ),
            LegalSectionResponse(
                "2. Your account",
                "You must be at least 16 years old, or the age of digital consent where you live if " +
                    "higher, to use the Service.\n\n" +
                    "- Give accurate information and keep it up to date.\n" +
                    "- Keep your password secret. You are responsible for what happens under your account.\n" +
                    "- Tell us at once if you suspect unauthorized access.\n" +
                    "- One person per account. Do not share credentials."
            ),
            LegalSectionResponse(
                "3. Your content",
                "You own the documents, text, images and other material you create or upload " +
                    "(\"Your Content\"). We do not claim ownership of it.\n\n" +
                    "You give us a limited, worldwide, non-exclusive license to host, store, sync, process " +
                    "and display Your Content only as needed to run the Service for you and the people you " +
                    "share it with, and to keep backups. This license ends when you delete Your Content, " +
                    "except for copies kept in backups for a limited time.\n\n" +
                    "You are responsible for Your Content and for having the rights to it."
            ),
            LegalSectionResponse(
                "4. Acceptable use",
                "You agree not to:\n\n" +
                    "- break the law or infringe the rights of others;\n" +
                    "- upload malware or try to disrupt, probe or overload the Service;\n" +
                    "- access accounts, data or systems you are not authorized to access;\n" +
                    "- reverse engineer the Service, except where the law or an open-source license allows it;\n" +
                    "- use the Service to send spam or to harass, defraud or harm anyone;\n" +
                    "- resell the Service or build a competing product from it without our written consent."
            ),
            LegalSectionResponse(
                "5. AI features",
                "Some features send text you choose, or the document you are working on, to AI " +
                    "providers to generate a result, such as writing help, header images or equation " +
                    "recognition. Outputs can be wrong or incomplete: review them before relying on them.\n\n" +
                    "As between you and us, you own the outputs generated from Your Content to the extent " +
                    "the law allows. Features that run on your own device or a local model keep the " +
                    "content on your device."
            ),
            LegalSectionResponse(
                "6. Plans, billing and storage",
                "Some features need a paid plan or are limited by quotas, such as storage or AI usage. " +
                    "Prices, limits and billing terms are shown before you buy. Paid plans renew until " +
                    "cancelled, and you can cancel at any time; the cancellation takes effect at the end of " +
                    "the period already paid for. Except where the law requires otherwise, fees are not " +
                    "refundable.\n\n" +
                    "We may change plans and prices with reasonable prior notice."
            ),
            LegalSectionResponse(
                "7. Open source",
                "Parts of Writeopia are published under open-source licenses. Those licenses govern " +
                    "the code they cover. These Terms govern your use of the hosted Service."
            ),
            LegalSectionResponse(
                "8. Availability and changes",
                "We work to keep the Service available but do not promise it will be uninterrupted or " +
                    "error-free. We may change, suspend or discontinue features, and we will give notice " +
                    "when a change materially affects you.\n\n" +
                    "Writeopia is offline-first, but you should keep your own copies of important content: " +
                    "you can export your documents at any time."
            ),
            LegalSectionResponse(
                "9. Suspension and termination",
                "You can stop using the Service and delete your account at any time from the app. " +
                    "We may suspend or end your access if you break these Terms, put the Service or other " +
                    "people at risk, or if the law requires it. When your account is deleted we delete " +
                    "Your Content as described in the Privacy Policy."
            ),
            LegalSectionResponse(
                "10. Disclaimers",
                "The Service is provided \"as is\" and \"as available\". To the extent the law allows, " +
                    "we disclaim all warranties, including merchantability, fitness for a particular purpose " +
                    "and non-infringement."
            ),
            LegalSectionResponse(
                "11. Limitation of liability",
                "To the extent the law allows, Writeopia is not liable for indirect, incidental, special, " +
                    "consequential or punitive damages, or for lost profits, revenue, data or goodwill. " +
                    "Our total liability for any claim relating to the Service is limited to the greater " +
                    "of the amount you paid us in the 12 months before the claim and USD 50.\n\n" +
                    "Nothing in these Terms limits liability that cannot be limited by law."
            ),
            LegalSectionResponse(
                "12. Changes to these Terms",
                "We may update these Terms. When we make a material change we will tell you in the app " +
                    "or by email before it applies. If you keep using the Service after the new version " +
                    "takes effect, you accept it. If you do not accept it, you can delete your account."
            ),
            LegalSectionResponse(
                "13. General",
                "These Terms are the entire agreement between you and Writeopia about the Service. If a " +
                    "part is found unenforceable, the rest stays in force. Not enforcing a right is not a " +
                    "waiver of it. You may not transfer these Terms without our consent; we may transfer " +
                    "them as part of a merger or sale of the business. The law of the place where Writeopia " +
                    "is established governs these Terms, without regard to conflict-of-law rules, and " +
                    "courts there have jurisdiction, except where consumer protection law gives you the " +
                    "right to use your local courts."
            ),
            LegalSectionResponse(
                "14. Contact",
                "Questions about these Terms: $CONTACT"
            ),
        )
    )

    val privacy = LegalDocumentResponse(
        type = PRIVACY,
        version = VERSION,
        effectiveDate = EFFECTIVE_DATE,
        title = "Privacy Policy",
        sections = listOf(
            LegalSectionResponse(
                "1. Introduction",
                "This Privacy Policy explains what personal data Writeopia collects, why, how we use " +
                    "and protect it, and the choices you have. It applies to the Writeopia apps, website " +
                    "and services. Writeopia is the controller of the data described here."
            ),
            LegalSectionResponse(
                "2. Data we collect",
                "Data you give us:\n\n" +
                    "- Account data: name, username, email address, password (stored only as a salted hash) " +
                    "and workspace name.\n" +
                    "- Your Content: documents, folders, images, drawings and other material you create or " +
                    "upload, and the settings and preferences you save.\n" +
                    "- Messages you send us, for example to support.\n\n" +
                    "Data collected automatically:\n\n" +
                    "- Technical data: app version, device and operating system type, language, IP address " +
                    "and timestamps of requests, used for security, debugging and abuse prevention.\n" +
                    "- Usage data needed to run the Service, such as storage used and AI usage against your " +
                    "plan limits.\n\n" +
                    "If you only use Writeopia offline, your content stays on your device and we do not " +
                    "receive it."
            ),
            LegalSectionResponse(
                "3. How we use data",
                "- To provide the Service: create your account, store and sync your content, and send " +
                    "emails you need such as verification codes and password resets.\n" +
                    "- To run AI features you ask for.\n" +
                    "- To bill paid plans and enforce quotas.\n" +
                    "- To keep the Service secure and prevent fraud and abuse.\n" +
                    "- To fix problems and improve the Service.\n" +
                    "- To comply with legal obligations and to establish or defend legal claims.\n\n" +
                    "We do not sell your personal data, and we do not use Your Content to show you " +
                    "advertising."
            ),
            LegalSectionResponse(
                "4. Legal bases (EEA, UK)",
                "Where the GDPR applies we rely on: performing our contract with you (providing the " +
                    "Service); our legitimate interests (security, abuse prevention, improving the Service); " +
                    "your consent where we ask for it, which you can withdraw at any time; and legal " +
                    "obligations."
            ),
            LegalSectionResponse(
                "5. AI providers",
                "When you use a cloud AI feature, the text, image or drawing needed for that request is " +
                    "sent through our servers to an AI provider, such as Google, that processes it and " +
                    "returns a result. We send only what the request needs. We do not allow providers to " +
                    "use this data to train their models under the terms we use. Local AI features send " +
                    "nothing to us or to a provider."
            ),
            LegalSectionResponse(
                "6. Sharing",
                "We share personal data only:\n\n" +
                    "- with service providers that process it for us under contract: cloud hosting and " +
                    "storage, email delivery, AI providers and payment processing;\n" +
                    "- with other people, when you share or collaborate on a document;\n" +
                    "- when the law requires it, or to protect rights, safety and security;\n" +
                    "- in a merger, acquisition or sale of assets, with notice to you.\n\n" +
                    "Our providers may process data in countries other than yours. Where required we use " +
                    "safeguards such as standard contractual clauses."
            ),
            LegalSectionResponse(
                "7. Retention",
                "We keep account data and Your Content while your account is active. When you delete " +
                    "your account we delete your personal data and Your Content, except what we must keep " +
                    "by law or for legitimate purposes such as fraud prevention, and copies in backups, " +
                    "which expire on a fixed schedule. Server logs are kept for a limited time."
            ),
            LegalSectionResponse(
                "8. Security",
                "We use encryption in transit, hashed passwords, access controls and monitoring to protect " +
                    "your data. No system is perfectly secure: use a strong, unique password and tell us if " +
                    "you suspect a problem."
            ),
            LegalSectionResponse(
                "9. Your rights",
                "Depending on where you live, you can:\n\n" +
                    "- access and export your data (you can export documents from the app);\n" +
                    "- correct data that is wrong;\n" +
                    "- delete your account and data from the app;\n" +
                    "- object to or restrict some processing, and withdraw consent;\n" +
                    "- complain to your data protection authority.\n\n" +
                    "To use a right we cannot satisfy from the app, write to $CONTACT. We may need to " +
                    "verify your identity first."
            ),
            LegalSectionResponse(
                "10. Cookies and local storage",
                "The web app uses strictly necessary cookies (HttpOnly) to keep you signed in, and local " +
                    "storage for your preferences. We do not use advertising cookies."
            ),
            LegalSectionResponse(
                "11. Children",
                "The Service is not directed to children under 16, and we do not knowingly collect their " +
                    "data. If you believe a child has given us data, contact us and we will delete it."
            ),
            LegalSectionResponse(
                "12. Changes to this policy",
                "We may update this policy. For material changes we will tell you in the app or by email " +
                    "before they apply. The effective date at the top shows the current version."
            ),
            LegalSectionResponse(
                "13. Contact",
                "Questions or requests about privacy: $CONTACT"
            ),
        )
    )
}
