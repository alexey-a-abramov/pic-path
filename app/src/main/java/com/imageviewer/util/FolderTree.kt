package com.imageviewer.util

import com.imageviewer.data.model.FolderEntry
import com.imageviewer.data.model.NavigableFolderEntry

/**
 * Builds an in-memory tree of folder paths from a flat list of leaf folders
 * (folders that directly contain images), and exposes navigation helpers used
 * by the Folders browse mode.
 */
class FolderTree private constructor(private val root: Node) {

    private data class Node(
        val path: String,
        val name: String,
        val children: LinkedHashMap<String, Node> = LinkedHashMap(),
        var directImages: Int = 0,
        var totalImages: Int = 0,
        var sampleUri: String? = null,
        var sampleMime: String? = null,
        var lastModified: Long = 0L
    )

    /** All known leaf paths (folders that have at least one image directly). */
    val leafPaths: List<String> by lazy {
        val out = mutableListOf<String>()
        fun walk(n: Node) {
            if (n.directImages > 0) out.add(n.path)
            n.children.values.forEach(::walk)
        }
        walk(root)
        out
    }

    /** Root path used when no specific folder is selected (the deepest common
     *  ancestor of all known folders, or empty if the tree is empty). */
    val rootPath: String get() = root.path

    /** Direct children of [path] (or of the tree root when [path] == rootPath). */
    fun childrenOf(path: String): List<NavigableFolderEntry> {
        val node = locate(path) ?: return emptyList()
        return node.children.values
            .sortedWith(compareByDescending<Node> { it.lastModified }.thenBy { it.name })
            .map { it.toEntry() }
    }

    /** Search across the whole tree by case-insensitive substring on the path. */
    fun search(query: String): List<NavigableFolderEntry> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        val out = mutableListOf<NavigableFolderEntry>()
        fun walk(n: Node) {
            if (n !== root && n.path.lowercase().contains(needle)) out.add(n.toEntry())
            n.children.values.forEach(::walk)
        }
        walk(root)
        // Most-recent first.
        return out.sortedByDescending { entry -> locate(entry.path)?.lastModified ?: 0L }
    }

    /** Path segments from rootPath to [path], used to render the breadcrumb. */
    fun breadcrumb(path: String): List<Crumb> {
        val out = mutableListOf<Crumb>()
        out.add(Crumb(label = root.name.ifEmpty { "/" }, path = root.path))
        if (path == root.path) return out
        val rel = if (root.path.isEmpty()) path.trimStart('/') else path.removePrefix(root.path).trimStart('/')
        if (rel.isEmpty()) return out
        var acc = root.path
        for (segment in rel.split('/').filter { it.isNotEmpty() }) {
            acc = if (acc.isEmpty()) "/$segment" else "$acc/$segment"
            out.add(Crumb(label = segment, path = acc))
        }
        return out
    }

    /** True when [path] is a node in the tree. */
    fun contains(path: String): Boolean = locate(path) != null

    private fun locate(path: String): Node? {
        if (path == root.path) return root
        if (root.path.isNotEmpty() && !path.startsWith(root.path + "/") && path != root.path) return null
        val rel = if (root.path.isEmpty()) path.trimStart('/') else path.removePrefix(root.path).trimStart('/')
        var node = root
        for (segment in rel.split('/').filter { it.isNotEmpty() }) {
            node = node.children[segment] ?: return null
        }
        return node
    }

    private fun Node.toEntry(): NavigableFolderEntry = NavigableFolderEntry(
        path = path,
        name = name.ifEmpty { path.ifBlank { "/" } },
        hasChildren = children.isNotEmpty(),
        directImageCount = directImages,
        totalImageCount = totalImages,
        sampleUri = sampleUri,
        sampleMimeType = sampleMime
    )

    data class Crumb(val label: String, val path: String)

    companion object {
        /** Build a tree, anchored at the deepest common ancestor of every input
         *  folder so the user doesn't have to navigate through empty levels. */
        fun build(rows: List<FolderEntry>): FolderTree {
            if (rows.isEmpty()) return FolderTree(Node(path = "", name = ""))
            val anchor = commonAncestor(rows.map { it.folder })
            val root = Node(path = anchor, name = anchor.substringAfterLast('/'))
            for (row in rows) {
                if (!row.folder.startsWith(anchor)) continue
                val rel = row.folder.removePrefix(anchor).trimStart('/')
                var node = root
                var acc = anchor
                if (rel.isEmpty()) {
                    node.directImages += row.count
                } else {
                    for (segment in rel.split('/').filter { it.isNotEmpty() }) {
                        acc = if (acc.isEmpty()) "/$segment" else "$acc/$segment"
                        node = node.children.getOrPut(segment) { Node(acc, segment) }
                    }
                    node.directImages += row.count
                }
                if (row.lastModified > node.lastModified) {
                    node.lastModified = row.lastModified
                    node.sampleUri = row.sampleUri
                    node.sampleMime = row.sampleMimeType
                }
            }
            // Aggregate totals + propagate latest-modified up the tree.
            fun aggregate(n: Node): Pair<Int, Long> {
                var total = n.directImages
                var lm = n.lastModified
                for (c in n.children.values) {
                    val (childTotal, childLm) = aggregate(c)
                    total += childTotal
                    if (childLm > lm) lm = childLm
                }
                n.totalImages = total
                n.lastModified = lm
                return total to lm
            }
            aggregate(root)
            return FolderTree(root)
        }

        private fun commonAncestor(paths: List<String>): String {
            if (paths.isEmpty()) return ""
            val splits = paths.map { it.trimStart('/').split('/') }
            val shortest = splits.minOf { it.size }
            val out = mutableListOf<String>()
            for (i in 0 until shortest) {
                val seg = splits[0][i]
                if (splits.all { it[i] == seg }) out.add(seg) else break
            }
            return if (out.isEmpty()) "" else "/" + out.joinToString("/")
        }
    }
}
