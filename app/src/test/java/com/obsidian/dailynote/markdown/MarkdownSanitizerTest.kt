package com.obsidian.dailynote.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSanitizerTest {

    @Test
    fun stripFrontmatter_removesYamlHeader() {
        val input = """
            ---
            date: 2026-10-22
            tags: [journal, daily]
            aliases: []
            ---
            # Today's Focus
            - [ ] Complete Android Widget
        """.trimIndent()

        val stripped = MarkdownSanitizer.stripFrontmatter(input)
        assertFalse(stripped.contains("date: 2026-10-22"))
        assertFalse(stripped.contains("tags: [journal, daily]"))
        assertTrue(stripped.startsWith("# Today's Focus"))
    }

    @Test
    fun cleanInlineFormatting_removesWikilinksAndMarkdownLinks() {
        val input = "Review [[Architecture Doc|Design Document]] and read [Kotlin Docs](https://kotlinlang.org)."
        val cleaned = MarkdownSanitizer.cleanInlineFormatting(input)
        assertEquals("Review Design Document and read Kotlin Docs.", cleaned)

        val directWikilink = "Check [[Standup Notes]] today."
        assertEquals("Check Standup Notes today.", MarkdownSanitizer.cleanInlineFormatting(directWikilink))
    }

    @Test
    fun cleanInlineFormatting_removesBoldItalicCodeStrikethrough() {
        val input = "This is **bold**, *italic*, ~~deleted~~, and `inline code`."
        val cleaned = MarkdownSanitizer.cleanInlineFormatting(input)
        assertEquals("This is bold, italic, deleted, and inline code.", cleaned)
    }

    @Test
    fun parseBlocks_identifiesTasksAndHeadings() {
        val input = """
            # Morning Plan
            - [ ] Buy groceries
            - [x] Exercise
            - Simple bullet
        """.trimIndent()

        val blocks = MarkdownSanitizer.parseBlocks(input)
        assertEquals(4, blocks.size)

        assertEquals(BlockType.HEADING_1, blocks[0].type)
        assertEquals("Morning Plan", blocks[0].text)

        assertEquals(BlockType.TASK_TODO, blocks[1].type)
        assertEquals("Buy groceries", blocks[1].text)

        assertEquals(BlockType.TASK_DONE, blocks[2].type)
        assertEquals("Exercise", blocks[2].text)

        assertEquals(BlockType.BULLET_ITEM, blocks[3].type)
        assertEquals("Simple bullet", blocks[3].text)
    }

    @Test
    fun sanitizeToString_convertsTaskBoxesToUnicodeSymbols() {
        val input = """
            - [ ] First task
            - [x] Completed task
        """.trimIndent()

        val result = MarkdownSanitizer.sanitizeToString(input)
        assertTrue(result.contains("☐ First task"))
        assertTrue(result.contains("☑ Completed task"))
    }

    @Test
    fun parseBlocks_excludesJournalingSection_withSubheadingsAndContent() {
        val input = """
            # Today's Focus
            - [ ] Complete Android Widget
            - [x] Morning run

            # Journaling
            Today I reflected on the project.
            It felt very productive.

            ### Gratitude
            1. Good coffee
            2. Sunshine

            # Evening Plan
            - [ ] Read 20 pages
        """.trimIndent()

        val blocks = MarkdownSanitizer.parseBlocks(input)

        // Verify Journaling heading and its contents are excluded
        assertFalse(blocks.any { it.text.contains("Journaling", ignoreCase = true) })
        assertFalse(blocks.any { it.text.contains("reflected on the project") })
        assertFalse(blocks.any { it.text.contains("Gratitude") })
        assertFalse(blocks.any { it.text.contains("Good coffee") })

        // Verify other sections remain intact
        assertTrue(blocks.any { it.text == "Today's Focus" })
        assertTrue(blocks.any { it.text == "Complete Android Widget" })
        assertTrue(blocks.any { it.text == "Evening Plan" })
        assertTrue(blocks.any { it.text == "Read 20 pages" })
    }

    @Test
    fun sanitizeToString_omitsJournalingSection() {
        val input = """
            # Tasks
            - [ ] Task A

            ## Journaling
            Private notes here.

            # Other
            - Note B
        """.trimIndent()

        val result = MarkdownSanitizer.sanitizeToString(input)
        assertFalse(result.contains("Journaling"))
        assertFalse(result.contains("Private notes here"))
        assertTrue(result.contains("Task A"))
        assertTrue(result.contains("Note B"))
    }

    @Test
    fun isSectionExcluded_matchesVariations() {
        val excluded = setOf("journaling")
        assertTrue(MarkdownSanitizer.isSectionExcluded("Journaling", excluded))
        assertTrue(MarkdownSanitizer.isSectionExcluded("journaling", excluded))
        assertTrue(MarkdownSanitizer.isSectionExcluded("Journaling:", excluded))
        assertTrue(MarkdownSanitizer.isSectionExcluded("📝 Journaling", excluded))
        assertFalse(MarkdownSanitizer.isSectionExcluded("Focus", excluded))
        assertFalse(MarkdownSanitizer.isSectionExcluded("Tasks", excluded))
    }

    @Test
    fun parseBlocks_hidesCompletedTasks_whenEnabled() {
        val input = """
            # Daily Tasks
            - [ ] Buy groceries
            - [x] Exercise in morning
            - [X] Reply to emails
            - [ ] Ship Android feature
        """.trimIndent()

        val blocks = MarkdownSanitizer.parseBlocks(
            rawMarkdown = input,
            hideCompletedTasks = true
        )

        assertTrue(blocks.any { it.text == "Buy groceries" })
        assertTrue(blocks.any { it.text == "Ship Android feature" })
        assertFalse(blocks.any { it.text == "Exercise in morning" })
        assertFalse(blocks.any { it.text == "Reply to emails" })
        assertEquals(0, blocks.count { it.type == BlockType.TASK_DONE })
    }

    @Test
    fun parseBlocks_showsCompletedTasks_whenDisabled() {
        val input = """
            - [ ] Task 1
            - [x] Done task
        """.trimIndent()

        val blocks = MarkdownSanitizer.parseBlocks(
            rawMarkdown = input,
            hideCompletedTasks = false
        )

        assertEquals(2, blocks.size)
        assertTrue(blocks.any { it.type == BlockType.TASK_DONE && it.text == "Done task" })
    }

    @Test
    fun sanitizeToString_hidesCompletedTasks_whenEnabled() {
        val input = """
            - [ ] Active todo
            - [x] Finished item
        """.trimIndent()

        val result = MarkdownSanitizer.sanitizeToString(
            rawMarkdown = input,
            hideCompletedTasks = true
        )

        assertTrue(result.contains("Active todo"))
        assertFalse(result.contains("Finished item"))
    }

    @Test
    fun calculateIndentLevel_correctlyCalculatesTabsAndSpaces() {
        assertEquals(0, MarkdownSanitizer.calculateIndentLevel(""))
        assertEquals(1, MarkdownSanitizer.calculateIndentLevel("\t"))
        assertEquals(2, MarkdownSanitizer.calculateIndentLevel("\t\t"))
        assertEquals(1, MarkdownSanitizer.calculateIndentLevel("  "))
        assertEquals(2, MarkdownSanitizer.calculateIndentLevel("    "))
        assertEquals(2, MarkdownSanitizer.calculateIndentLevel("\t  "))
        assertEquals(3, MarkdownSanitizer.calculateIndentLevel("\t\t\t"))
    }

    @Test
    fun parseBlocks_preservesIndentation_forTasksWithTabsAndSpaces() {
        val input = """
            - [ ] Top level task
            	- [ ] Subtask with tab
            		- [ ] Sub-subtask with two tabs
              - [ ] Subtask with two spaces
                - [ ] Subtask with four spaces
        """.trimIndent()

        val blocks = MarkdownSanitizer.parseBlocks(input)
        assertEquals(5, blocks.size)

        assertEquals("Top level task", blocks[0].text)
        assertEquals(0, blocks[0].indentLevel)

        assertEquals("Subtask with tab", blocks[1].text)
        assertEquals(1, blocks[1].indentLevel)

        assertEquals("Sub-subtask with two tabs", blocks[2].text)
        assertEquals(2, blocks[2].indentLevel)

        assertEquals("Subtask with two spaces", blocks[3].text)
        assertEquals(1, blocks[3].indentLevel)

        assertEquals("Subtask with four spaces", blocks[4].text)
        assertEquals(2, blocks[4].indentLevel)
    }

    @Test
    fun parseBlocks_preservesIndentation_forBulletsAndNumberedItems() {
        val input = """
            - Parent bullet
            	- Child bullet tab
              - Child bullet spaces
            1. Parent number
            	1. Child number tab
              2. Child number spaces
        """.trimIndent()

        val blocks = MarkdownSanitizer.parseBlocks(input)
        assertEquals(6, blocks.size)

        assertEquals(0, blocks[0].indentLevel)
        assertEquals(1, blocks[1].indentLevel)
        assertEquals(1, blocks[2].indentLevel)
        assertEquals(0, blocks[3].indentLevel)
        assertEquals(1, blocks[4].indentLevel)
        assertEquals(1, blocks[5].indentLevel)
    }

    @Test
    fun parseBlocks_preservesIndentation_forParagraphsAndNotes() {
        val input = """
            - [ ] Complete feature
            	Remember to test on Android
                Additional note with spaces
        """.trimIndent()

        val blocks = MarkdownSanitizer.parseBlocks(input)
        assertEquals(3, blocks.size)

        assertEquals(BlockType.TASK_TODO, blocks[0].type)
        assertEquals(0, blocks[0].indentLevel)

        assertEquals(BlockType.PARAGRAPH, blocks[1].type)
        assertEquals("Remember to test on Android", blocks[1].text)
        assertEquals(1, blocks[1].indentLevel)

        assertEquals(BlockType.PARAGRAPH, blocks[2].type)
        assertEquals("Additional note with spaces", blocks[2].text)
        assertEquals(2, blocks[2].indentLevel)
    }

    @Test
    fun sanitizeToString_showsWhitespaceForIndentedItems() {
        val input = """
            - [ ] Top task
            	- [ ] Sub task
            		- Note under subtask
        """.trimIndent()

        val result = MarkdownSanitizer.sanitizeToString(input)
        val lines = result.lines()

        assertEquals("☐ Top task", lines[0])
        assertEquals("  ☐ Sub task", lines[1])
        assertEquals("    • Note under subtask", lines[2])
    }
}
