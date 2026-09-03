package com.nodespark.env

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext

/**
 * Key completion inside a .env file, taken from the other .env files next to it — in practice the
 * committed .env.example, which is where a project actually documents its variables. After the `=`
 * it offers the values those files use for the same key.
 */
class EnvCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(EnvLanguage),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) = complete(parameters, result)
            },
        )
    }

    private fun complete(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile
        val virtualFile = file.virtualFile ?: return
        val text = file.text
        val offset = parameters.offset.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (offset - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val before = text.substring(lineStart, offset)
        if (before.trimStart().startsWith("#")) return

        val siblings = EnvSiblings.siblings(virtualFile).mapNotNull { sibling ->
            readText(sibling)?.let { sibling.name to EnvFile.parse(it).pairs }
        }
        if (siblings.isEmpty()) return

        val separator = before.indexOf('=')
        if (separator < 0) {
            val present = EnvFile.parse(text).pairs.mapTo(HashSet()) { it.key }
            val seen = HashSet<String>()
            for ((name, pairs) in siblings) {
                for (pair in pairs) {
                    if (pair.key in present || !seen.add(pair.key)) continue
                    result.addElement(
                        LookupElementBuilder.create(pair.key)
                            .withTypeText(name)
                            .withInsertHandler { ctx, _ ->
                                val doc = ctx.document
                                val end = ctx.tailOffset
                                if (end >= doc.textLength || doc.charsSequence[end] != '=') {
                                    doc.insertString(end, "=")
                                    ctx.editor.caretModel.moveToOffset(end + 1)
                                }
                            },
                    )
                }
            }
            return
        }

        val key = before.substring(0, separator).removePrefix("export").trim()
        val seen = HashSet<String>()
        for ((name, pairs) in siblings) {
            for (pair in pairs) {
                if (pair.key != key || pair.value.isEmpty() || !seen.add(pair.value)) continue
                result.addElement(LookupElementBuilder.create(pair.value).withTypeText(name))
            }
        }
    }

    private fun readText(file: VirtualFile): String? = try {
        if (file.length > MAX_BYTES) null else VfsUtilCore.loadText(file)
    } catch (e: Exception) {
        logger<EnvCompletionContributor>().debug("cannot read ${file.name}", e)
        null
    }

    private companion object {
        const val MAX_BYTES = 512L * 1024
    }
}

/** Warns on a key assigned twice: the later assignment silently wins, which is easy to miss. */
class EnvDuplicateKeyAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PsiFile || element.language != EnvLanguage) return
        val length = element.textLength
        for ((key, start, end) in EnvFile.duplicateKeyRanges(element.text)) {
            if (end > length) continue
            holder.newAnnotation(HighlightSeverity.WARNING, "Duplicate key '$key' — the last assignment wins")
                .range(TextRange(start, end))
                .create()
        }
    }
}
