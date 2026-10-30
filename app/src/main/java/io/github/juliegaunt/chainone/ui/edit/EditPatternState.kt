package io.github.juliegaunt.chainone.ui.edit

import android.net.Uri
import io.github.juliegaunt.chainone.data.db.Pattern
import io.github.juliegaunt.chainone.data.db.PatternStatus
import io.github.juliegaunt.chainone.data.importer.StagedImport
import io.github.juliegaunt.chainone.data.repository.PatternDraft
import java.io.File

/** The edit screen's fields as typed so far. */
data class PatternForm(
    val title: String = "",
    val designer: String = "",
    val sourceUrl: String = "",
    val hookSize: String = "",
    val yarnWeight: String = "",
    val materials: String = "",
    val instructions: String = "",
    val notes: String = "",
    val status: PatternStatus = PatternStatus.WANT_TO_MAKE,
    val tags: List<String> = emptyList(),
) {
    fun toDraft() = PatternDraft(
        title = title,
        designer = designer,
        sourceUrl = sourceUrl,
        hookSize = hookSize,
        yarnWeight = yarnWeight,
        materials = materials,
        instructions = instructions,
        notes = notes,
        status = status,
        tags = tags,
    )

    companion object {
        fun from(staged: StagedImport) = PatternForm(
            title = staged.title,
            designer = staged.designer,
            sourceUrl = staged.sourceUrl,
            hookSize = staged.hookSize,
            yarnWeight = staged.yarnWeight,
            materials = staged.materials,
            instructions = staged.instructions,
        )

        fun from(pattern: Pattern) = PatternForm(
            title = pattern.title,
            designer = pattern.designer,
            sourceUrl = pattern.sourceUrl.orEmpty(),
            hookSize = pattern.hookSize,
            yarnWeight = pattern.yarnWeight,
            materials = pattern.materials,
            instructions = pattern.instructions,
            notes = pattern.notes,
            status = pattern.status,
        )
    }
}

/** What the review banner says about a pattern imported from a website or a PDF. */
data class ImportReview(
    val host: String,
    /** String resources of the fields the page didn't provide. */
    val missingFields: List<Int>,
    val foundInstructions: Boolean,
    val hasPageCopy: Boolean,
    /** Set when the import is a PDF, which Chain One can't read the details out of. */
    val pdfName: String? = null,
)

/** A PDF in the edit screen's list: already saved, waiting in an import, or just picked. */
data class PdfEntry(
    val key: String,
    val name: String,
    val savedId: Long? = null,
    val stagedFile: File? = null,
    val pickedUri: Uri? = null,
)

data class EditPatternUiState(
    val isNew: Boolean,
    /** Set when reviewing a pattern downloaded from a website, before it's saved. */
    val review: ImportReview? = null,
    val isLoading: Boolean = !isNew,
    val form: PatternForm = PatternForm(),
    /** The form as it was when the screen opened, to detect unsaved changes. */
    val initialForm: PatternForm = PatternForm(),
    /** The cover already saved (or, when reviewing an import, the downloaded one). */
    val savedCover: File? = null,
    /** A photo picked on this screen but not saved yet. */
    val pickedCover: Uri? = null,
    val removeCover: Boolean = false,
    val pdfs: List<PdfEntry> = emptyList(),
    /** The PDFs as they were when the screen opened (or last saved). */
    val initialPdfs: List<PdfEntry> = emptyList(),
    val showTitleError: Boolean = false,
    val isSaving: Boolean = false,
    val coverCopyFailed: Boolean = false,
    val importFilesFailed: Boolean = false,
    val pdfCopyFailed: Boolean = false,
    /** Text typed in the Tags box that hasn't been turned into a tag yet. */
    val tagInput: String = "",
    /** Every tag in the library, to suggest while typing. */
    val allTags: List<String> = emptyList(),
    /** Set when saving finished; the screen then navigates away. */
    val savedId: Long? = null,
) {
    val coverModel: Any? get() = pickedCover ?: savedCover.takeUnless { removeCover }

    val hasChanges: Boolean
        get() = form != initialForm || pickedCover != null || removeCover || pdfs != initialPdfs || tagInput.isNotBlank()

    /** Existing tags matching what's typed (or, before typing, any), leaving out ones already added. */
    val tagSuggestions: List<String>
        get() = allTags
            .filter { tag -> form.tags.none { it.equals(tag, ignoreCase = true) } }
            .filter { it.contains(tagInput.trim(), ignoreCase = true) }
            .take(MAX_SUGGESTIONS)

    private companion object {
        const val MAX_SUGGESTIONS = 8
    }
}
