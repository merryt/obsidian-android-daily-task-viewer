package com.obsidian.dailynote.markdown

import java.util.regex.Pattern

enum class BlockType {
    HEADING_1,
    HEADING_2,
    HEADING_3,
    TASK_TODO,
    TASK_DONE,
    BULLET_ITEM,
    NUMBERED_ITEM,
    BLOCKQUOTE,
    DIVIDER,
    PARAGRAPH,
    EMPTY_LINE
}

data class SanitizedBlock(
    val type: BlockType,
    val text: String,
    val indentLevel: Int = 0
)

object MarkdownSanitizer {

    // Regex to match YAML frontmatter block at the beginning of the file
    private val FRONTMATTER_REGEX = Pattern.compile("^---\\r?\\n[\\s\\S]*?\\r?\\n---\\r?\\n?", Pattern.MULTILINE)

    // Regex for Obsidian Wikilinks: [[Target|Alias]] or [[Target]]
    private val WIKILINK_ALIAS_REGEX = Regex("""\[\[(?:[^|\]]+\|)?([^\]]+)\]\]""")

    // Regex for standard Markdown links: [Title](Url)
    private val MD_LINK_REGEX = Regex("""\[([^\]]+)\]\([^)]+\)""")

    // Regex for Markdown images: ![alt](url) and ![[embed]]
    private val MD_IMAGE_REGEX = Regex("""!\[([^\]]*)\]\([^)]+\)""")
    private val WIKILINK_EMBED_REGEX = Regex("""!\[\[(?:[^|\]]+\|)?([^\]]+)\]\]""")

    // Regex for bold, italic, strikethrough, inline code
    private val BOLD_REGEX = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
    private val ITALIC_REGEX = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)|(?<!_)_(?!_)(.+?)(?<!_)_(?!_)""")
    private val STRIKETHROUGH_REGEX = Regex("""~~(.+?)~~""")
    private val INLINE_CODE_REGEX = Regex("""`([^`]+)`""")

    // Task and list patterns
    private val TASK_TODO_REGEX = Regex("""^(\s*)[-*+]\s+\[\s\]\s+(.*)$""")
    private val TASK_DONE_REGEX = Regex("""^(\s*)[-*+]\s+\[[xX]\]\s+(.*)$""")
    private val TASK_OTHER_REGEX = Regex("""^(\s*)[-*+]\s+\[[^\]]\]\s+(.*)$""")
    private val BULLET_REGEX = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val NUMBERED_LIST_REGEX = Regex("""^(\s*)(\d+\.)\s+(.*)$""")
    private val HEADING_1_REGEX = Regex("""^#\s+(.*)$""")
    private val HEADING_2_REGEX = Regex("""^##\s+(.*)$""")
    private val HEADING_3_REGEX = Regex("""^###+\s+(.*)$""")
    private val BLOCKQUOTE_REGEX = Regex("""^>\s?(.*)$""")
    private val HORIZONTAL_RULE_REGEX = Regex("""^(\*{3,}|-{3,}|_{3,})$""")

    /**
     * Strips YAML frontmatter from Obsidian markdown.
     */
    fun stripFrontmatter(markdown: String): String {
        val matcher = FRONTMATTER_REGEX.matcher(markdown)
        return if (matcher.find() && matcher.start() == 0) {
            matcher.replaceFirst("")
        } else {
            markdown
        }
    }

    /**
     * Cleans inline Markdown formatting from a single line of text:
     * - Removes bold, italic, strikethrough, backtick markers
     * - Resolves Obsidian wikilinks [[Target|Alias]] -> Alias
     * - Resolves Markdown links [Title](URL) -> Title
     * - Cleans embedded media markers
     */
    fun cleanInlineFormatting(line: String): String {
        var result = line

        // Handle embedded images/notes
        result = WIKILINK_EMBED_REGEX.replace(result) { match -> match.groupValues[1] }
        result = MD_IMAGE_REGEX.replace(result) { match -> match.groupValues[1] }

        // Handle wikilinks and standard links
        result = WIKILINK_ALIAS_REGEX.replace(result) { match -> match.groupValues[1] }
        result = MD_LINK_REGEX.replace(result) { match -> match.groupValues[1] }

        // Handle text styles
        result = BOLD_REGEX.replace(result) { match ->
            match.groupValues[1].ifEmpty { match.groupValues[2] }
        }
        result = ITALIC_REGEX.replace(result) { match ->
            match.groupValues[1].ifEmpty { match.groupValues[2] }
        }
        result = STRIKETHROUGH_REGEX.replace(result) { match -> match.groupValues[1] }
        result = INLINE_CODE_REGEX.replace(result) { match -> match.groupValues[1] }

        return result
    }

    val DEFAULT_EXCLUDED_SECTIONS: Set<String> = setOf("journaling")

    private fun getHeadingLevelAndContent(line: String): Pair<Int, String>? {
        val trimmed = line.trim()
        val match = Regex("""^(#{1,6})\s+(.*)$""").find(trimmed) ?: return null
        val level = match.groupValues[1].length
        val content = match.groupValues[2]
        return Pair(level, content)
    }

    /**
     * Checks if a heading title matches any excluded section name (case-insensitive, handles symbols/emojis).
     */
    fun isSectionExcluded(headingTitle: String, excludedSections: Set<String>): Boolean {
        if (excludedSections.isEmpty()) return false
        val cleaned = headingTitle.replace(Regex("""[^\p{L}\p{N}\s]"""), " ").trim()
        val words = cleaned.split(Regex("""\s+""")).map { it.lowercase() }
        return excludedSections.any { excluded ->
            val target = excluded.trim().lowercase()
            target.isNotEmpty() && (
                headingTitle.trim().equals(target, ignoreCase = true) ||
                cleaned.equals(target, ignoreCase = true) ||
                words.contains(target)
            )
        }
    }

    /**
     * Parses the markdown content into a list of structured [SanitizedBlock] items,
     * stripping frontmatter, excluding configured sections (e.g. # Journaling),
     * optionally hiding completed tasks, and normalizing lines for widget rendering.
     */
    fun parseBlocks(
        rawMarkdown: String,
        excludedSections: Set<String> = DEFAULT_EXCLUDED_SECTIONS,
        hideCompletedTasks: Boolean = false
    ): List<SanitizedBlock> {
        val withoutFrontmatter = stripFrontmatter(rawMarkdown)
        val lines = withoutFrontmatter.lines()
        val blocks = mutableListOf<SanitizedBlock>()

        var inCodeFence = false
        var skippingSectionLevel: Int? = null

        for (rawLine in lines) {
            val trimmed = rawLine.trim()

            // Handle code block fences
            if (trimmed.startsWith("```")) {
                inCodeFence = !inCodeFence
                continue
            }
            if (inCodeFence) {
                if (skippingSectionLevel == null) {
                    blocks.add(SanitizedBlock(BlockType.PARAGRAPH, "    $rawLine"))
                }
                continue
            }

            // Check if this line is a heading
            val headingInfo = getHeadingLevelAndContent(trimmed)
            if (headingInfo != null) {
                val (level, content) = headingInfo
                val headingTitle = cleanInlineFormatting(content).trim()
                val isExcluded = isSectionExcluded(headingTitle, excludedSections)

                if (skippingSectionLevel != null) {
                    if (level <= skippingSectionLevel) {
                        // Current excluded section has ended!
                        skippingSectionLevel = if (isExcluded) level else null
                    }
                } else if (isExcluded) {
                    skippingSectionLevel = level
                }

                if (skippingSectionLevel != null) {
                    // This heading is part of an excluded section, skip emitting it
                    continue
                }

                // Not skipping, emit heading block based on level
                when (level) {
                    1 -> blocks.add(SanitizedBlock(BlockType.HEADING_1, headingTitle))
                    2 -> blocks.add(SanitizedBlock(BlockType.HEADING_2, headingTitle))
                    else -> blocks.add(SanitizedBlock(BlockType.HEADING_3, headingTitle))
                }
                continue
            }

            // If we are currently inside an excluded section, skip all content lines
            if (skippingSectionLevel != null) {
                continue
            }

            if (trimmed.isEmpty()) {
                // Collapse consecutive empty lines
                if (blocks.isNotEmpty() && blocks.last().type != BlockType.EMPTY_LINE) {
                    blocks.add(SanitizedBlock(BlockType.EMPTY_LINE, ""))
                }
                continue
            }

            // Other blocks (horizontal rules, tasks, bullets, blockquotes, paragraphs)
            when {
                HORIZONTAL_RULE_REGEX.matches(trimmed) -> {
                    blocks.add(SanitizedBlock(BlockType.DIVIDER, "───────────────"))
                }
                TASK_TODO_REGEX.matches(rawLine) -> {
                    val match = TASK_TODO_REGEX.find(rawLine)!!
                    val indent = match.groupValues[1].length / 2
                    val text = cleanInlineFormatting(match.groupValues[2])
                    blocks.add(SanitizedBlock(BlockType.TASK_TODO, text, indent))
                }
                TASK_DONE_REGEX.matches(rawLine) -> {
                    if (!hideCompletedTasks) {
                        val match = TASK_DONE_REGEX.find(rawLine)!!
                        val indent = match.groupValues[1].length / 2
                        val text = cleanInlineFormatting(match.groupValues[2])
                        blocks.add(SanitizedBlock(BlockType.TASK_DONE, text, indent))
                    }
                }
                TASK_OTHER_REGEX.matches(rawLine) -> {
                    val match = TASK_OTHER_REGEX.find(rawLine)!!
                    val indent = match.groupValues[1].length / 2
                    val text = cleanInlineFormatting(match.groupValues[2])
                    blocks.add(SanitizedBlock(BlockType.TASK_TODO, text, indent))
                }
                BULLET_REGEX.matches(rawLine) -> {
                    val match = BULLET_REGEX.find(rawLine)!!
                    val indent = match.groupValues[1].length / 2
                    val text = cleanInlineFormatting(match.groupValues[2])
                    blocks.add(SanitizedBlock(BlockType.BULLET_ITEM, text, indent))
                }
                NUMBERED_LIST_REGEX.matches(rawLine) -> {
                    val match = NUMBERED_LIST_REGEX.find(rawLine)!!
                    val indent = match.groupValues[1].length / 2
                    val num = match.groupValues[2]
                    val text = cleanInlineFormatting(match.groupValues[3])
                    blocks.add(SanitizedBlock(BlockType.NUMBERED_ITEM, "$num $text", indent))
                }
                BLOCKQUOTE_REGEX.matches(trimmed) -> {
                    val match = BLOCKQUOTE_REGEX.find(trimmed)!!
                    val text = cleanInlineFormatting(match.groupValues[1])
                    blocks.add(SanitizedBlock(BlockType.BLOCKQUOTE, text))
                }
                else -> {
                    blocks.add(SanitizedBlock(BlockType.PARAGRAPH, cleanInlineFormatting(trimmed)))
                }
            }
        }

        // Trim trailing empty line if any
        if (blocks.isNotEmpty() && blocks.last().type == BlockType.EMPTY_LINE) {
            blocks.removeAt(blocks.size - 1)
        }

        return blocks
    }

    /**
     * Converts raw markdown into a clean, human-readable plain text string
     * suitable for straightforward rendering in standard text components.
     */
    fun sanitizeToString(
        rawMarkdown: String,
        excludedSections: Set<String> = DEFAULT_EXCLUDED_SECTIONS,
        hideCompletedTasks: Boolean = false
    ): String {
        val blocks = parseBlocks(rawMarkdown, excludedSections, hideCompletedTasks)
        val sb = StringBuilder()

        for (block in blocks) {
            val indent = "  ".repeat(block.indentLevel)
            when (block.type) {
                BlockType.HEADING_1 -> sb.append("\n# ").append(block.text).append("\n")
                BlockType.HEADING_2 -> sb.append("\n## ").append(block.text).append("\n")
                BlockType.HEADING_3 -> sb.append("\n### ").append(block.text).append("\n")
                BlockType.TASK_TODO -> sb.append(indent).append("☐ ").append(block.text).append("\n")
                BlockType.TASK_DONE -> sb.append(indent).append("☑ ").append(block.text).append("\n")
                BlockType.BULLET_ITEM -> sb.append(indent).append("• ").append(block.text).append("\n")
                BlockType.NUMBERED_ITEM -> sb.append(indent).append(block.text).append("\n")
                BlockType.BLOCKQUOTE -> sb.append("│ ").append(block.text).append("\n")
                BlockType.DIVIDER -> sb.append("───────────────\n")
                BlockType.PARAGRAPH -> sb.append(block.text).append("\n")
                BlockType.EMPTY_LINE -> sb.append("\n")
            }
        }

        return sb.toString().trim()
    }
}
