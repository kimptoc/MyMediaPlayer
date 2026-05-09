package androidx.documentfile.provider

import android.net.Uri

class MockDocumentFile(parent: DocumentFile?, val mockUri: Uri, val mockName: String) : DocumentFile(parent) {
    override fun createFile(mimeType: String, displayName: String): DocumentFile? {
        val childUri = Uri.parse("$mockUri/$displayName")
        return MockDocumentFile(this, childUri, displayName)
    }

    override fun createDirectory(displayName: String): DocumentFile? = null
    override fun getUri(): Uri = mockUri
    override fun getName(): String = mockName
    override fun getType(): String? = null
    override fun isDirectory(): Boolean = true
    override fun isFile(): Boolean = false
    override fun isVirtual(): Boolean = false
    override fun lastModified(): Long = 0L
    override fun length(): Long = 0L
    override fun canRead(): Boolean = true
    override fun canWrite(): Boolean = true
    override fun delete(): Boolean = true
    override fun exists(): Boolean = true
    override fun listFiles(): Array<DocumentFile> = arrayOf()
    override fun renameTo(displayName: String): Boolean = true
}
