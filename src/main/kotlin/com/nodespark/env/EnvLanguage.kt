package com.nodespark.env

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.icons.AllIcons
import com.intellij.lang.ASTNode
import com.intellij.lang.Commenter
import com.intellij.lang.Language
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import javax.swing.Icon

object EnvLanguage : Language("DotEnv")

class EnvFileType private constructor() : LanguageFileType(EnvLanguage) {
    override fun getName(): String = "DotEnv"
    override fun getDescription(): String = "Environment variables (.env)"
    override fun getDefaultExtension(): String = "env"
    override fun getIcon(): Icon = AllIcons.FileTypes.Properties

    companion object {
        @JvmField val INSTANCE = EnvFileType()

        /** `.env`, `.env.local`, `.env.production`, `foo.env`. */
        fun matches(file: VirtualFile): Boolean = matches(file.name)

        fun matches(name: String): Boolean =
            name == ".env" || name.startsWith(".env.") || name.endsWith(".env")
    }
}

class EnvTokenType(debugName: String) : IElementType(debugName, EnvLanguage)

object EnvTokens {
    val COMMENT = EnvTokenType("COMMENT")
    val KEY = EnvTokenType("KEY")
    val EXPORT = EnvTokenType("EXPORT")
    val SEPARATOR = EnvTokenType("SEPARATOR")
    val VALUE = EnvTokenType("VALUE")
    val TEXT = EnvTokenType("TEXT")
    val FILE = IFileElementType(EnvLanguage)
}

/**
 * Colouring lexer. Deliberately line-based: a value that continues across lines inside quotes is
 * coloured as separate lines rather than one string.
 */
// ponytail: line-based lexer, no multi-line-quote state. Add a state only if the colouring of
// multi-line values is ever actually complained about; the parser in EnvFile already handles them.
class EnvLexer : LexerBase() {

    private data class Tok(val type: IElementType, val start: Int, val end: Int)

    private var buffer: CharSequence = ""
    private var endOffset = 0
    private var toks: List<Tok> = emptyList()
    private var index = 0

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.endOffset = endOffset
        this.toks = tokenize(buffer, startOffset, endOffset)
        this.index = 0
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = toks.getOrNull(index)?.type
    override fun getTokenStart(): Int = toks[index].start
    override fun getTokenEnd(): Int = toks[index].end
    override fun advance() { index++ }
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = endOffset

    private fun tokenize(text: CharSequence, from: Int, to: Int): List<Tok> {
        val out = ArrayList<Tok>()
        var i = from
        while (i < to) {
            var eol = i
            while (eol < to && text[eol] != '\n') eol++
            tokenizeLine(text, i, eol, out)
            if (eol < to) {
                out += Tok(TokenType.WHITE_SPACE, eol, eol + 1)
                eol++
            }
            i = eol
        }
        return out
    }

    private fun tokenizeLine(text: CharSequence, start: Int, end: Int, out: MutableList<Tok>) {
        var i = start
        while (i < end && text[i].isWhitespace()) i++
        if (i > start) out += Tok(TokenType.WHITE_SPACE, start, i)
        if (i >= end) return
        if (text[i] == '#') {
            out += Tok(EnvTokens.COMMENT, i, end)
            return
        }
        val line = text.subSequence(i, end).toString()
        val match = ASSIGNMENT.find(line)
        if (match == null) {
            out += Tok(EnvTokens.TEXT, i, end)
            return
        }
        val exportEnd = match.groups[1]?.range?.last?.plus(1)
        if (exportEnd != null) {
            out += Tok(EnvTokens.EXPORT, i, i + exportEnd)
            out += Tok(TokenType.WHITE_SPACE, i + exportEnd, i + match.groups[2]!!.range.first)
        }
        val key = match.groups[2]!!.range
        out += Tok(EnvTokens.KEY, i + key.first, i + key.last + 1)
        val eq = line.indexOf('=', key.last + 1)
        if (eq > key.last + 1) out += Tok(TokenType.WHITE_SPACE, i + key.last + 1, i + eq)
        out += Tok(EnvTokens.SEPARATOR, i + eq, i + eq + 1)
        if (i + eq + 1 < end) out += Tok(EnvTokens.VALUE, i + eq + 1, end)
    }

    private companion object {
        val ASSIGNMENT = Regex("^(export\\s+)?([A-Za-z_][A-Za-z0-9_.-]*)\\s*=")
    }
}

class EnvSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = EnvLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = when (tokenType) {
        EnvTokens.COMMENT -> pack(COMMENT)
        EnvTokens.KEY -> pack(KEY)
        EnvTokens.EXPORT -> pack(KEYWORD)
        EnvTokens.SEPARATOR -> pack(SEPARATOR)
        EnvTokens.VALUE -> pack(VALUE)
        else -> TextAttributesKey.EMPTY_ARRAY
    }

    companion object {
        val COMMENT: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("DOTENV_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
        val KEY: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("DOTENV_KEY", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
        val KEYWORD: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("DOTENV_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val SEPARATOR: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("DOTENV_SEPARATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val VALUE: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("DOTENV_VALUE", DefaultLanguageHighlighterColors.STRING)
    }
}

class EnvSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, file: VirtualFile?): SyntaxHighlighter = EnvSyntaxHighlighter()
}

class EnvPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, EnvLanguage) {
    override fun getFileType() = EnvFileType.INSTANCE
    override fun toString(): String = "DotEnv file"
}

/**
 * A flat PSI: every token becomes a leaf of the file node. There is nothing in a .env file worth a
 * tree, and a PSI file is all the platform needs to offer completion, comments and inspections.
 */
class EnvParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = EnvLexer()
    override fun getFileNodeType(): IFileElementType = EnvTokens.FILE
    override fun getCommentTokens(): TokenSet = TokenSet.create(EnvTokens.COMMENT)
    override fun getStringLiteralElements(): TokenSet = TokenSet.create(EnvTokens.VALUE)
    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)
    override fun createFile(viewProvider: FileViewProvider): PsiFile = EnvPsiFile(viewProvider)

    override fun createParser(project: Project?): PsiParser = PsiParser { root, builder ->
        val mark = builder.mark()
        while (!builder.eof()) builder.advanceLexer()
        mark.done(root)
        builder.treeBuilt
    }
}

class EnvCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "#"
    override fun getBlockCommentPrefix(): String? = null
    override fun getBlockCommentSuffix(): String? = null
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}
