/**
 * amagi and lisonge <https://github.com/gkd-kit/gkd>
 * Copyright (C) 2024 amagi and lisonge
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ps.gkd.data

import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.LogUtils
import com.ps.gkd.R
import com.ps.gkd.getSafeString
import kotlinx.serialization.Serializable
import com.ps.gkd.service.MAX_CHILD_SIZE
import com.ps.gkd.service.topActivityFlow
import com.ps.gkd.util.toast
import kotlin.system.measureTimeMillis

@Serializable
data class NodeInfo(
    val id: Int,
    val pid: Int,
    val idQf: Boolean?,
    val textQf: Boolean?,
    val attr: AttrInfo,
)

private data class TempNodeData(
    val node: AccessibilityNodeInfo,
    val parent: TempNodeData?,
    val index: Int,
    val depth: Int,
) {
    var id = 0
    val attr = AttrInfo.info2data(node, index, depth)
    var children: List<TempNodeData> = emptyList()

    var idQfInit = false
    var idQf: Boolean? = null
        set(value) {
            field = value
            idQfInit = true
        }
    var textQfInit = false
    var textQf: Boolean? = null
        set(value) {
            field = value
            textQfInit = true
        }
}

private fun getChildren(node: AccessibilityNodeInfo) = sequence {
    repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { i ->
        val child = node.getChild(i) ?: return@sequence
        yield(child)
    }
}

private const val MAX_KEEP_SIZE = 5000

// 先获取所有节点构建树结构, 然后再判断 idQf/textQf 如果存在一个能同时 idQf 和 textQf 的节点, 则认为 idQf 和 textQf 等价
fun info2nodeList(root: AccessibilityNodeInfo?): List<NodeInfo> {
    if (root == null) {
        return emptyList()
    }
    val nodes = mutableListOf<TempNodeData>()
    val collectTime = measureTimeMillis {
        val stack = mutableListOf<TempNodeData>()
        var times = 0
        stack.add(TempNodeData(root, null, 0, 0))
        while (stack.isNotEmpty()) {
            times++
            val node = stack.removeAt(stack.lastIndex)
            node.id = times - 1
            val children = getChildren(node.node).mapIndexed { i, child ->
                TempNodeData(
                    child, node, i, node.depth + 1
                )
            }.toList()
            node.children = children
            nodes.add(node)
            repeat(children.size) { i ->
                stack.add(children[children.size - i - 1])
            }
            if (times > MAX_KEEP_SIZE) {
                // https://github.com/gkd-kit/gkd/issues/28
                toast(String.format(getSafeString(R.string.max_keep_size_reached), MAX_KEEP_SIZE))
                LogUtils.w(
                    root.packageName, topActivityFlow.value.activityId, getSafeString(R.string.too_many_nodes)
                )
                break
            }
        }
    }
    val qfTime = measureTimeMillis {
        val idQfCache = mutableMapOf<String, List<AccessibilityNodeInfo>>()
        val textQfCache = mutableMapOf<String, List<AccessibilityNodeInfo>>()
        var idTextQf = false
        fun updateQf(n: TempNodeData) {
            if (!n.idQfInit && !n.attr.id.isNullOrEmpty()) {
                n.idQf = (idQfCache[n.attr.id]
                    ?: root.findAccessibilityNodeInfosByViewId(n.attr.id)).apply {
                    idQfCache[n.attr.id] = this
                }
                    .any { t -> t == n.node }

            }

            if (!n.textQfInit && !n.attr.text.isNullOrEmpty()) {
                n.textQf = (textQfCache[n.attr.text]
                    ?: root.findAccessibilityNodeInfosByText(n.attr.text)).apply {
                    textQfCache[n.attr.text] = this
                }
                    .any { t -> t == n.node }
            }

            if (n.idQf == true && n.textQf == true) {
                idTextQf = true
            }

            if (!n.idQfInit && n.idQf != null) {
                n.parent?.children?.forEach { c ->
                    c.idQf = n.idQf
                    if (idTextQf) {
                        c.textQf = n.textQf
                    }
                }
                if (n.idQf == true) {
                    var p = n.parent
                    while (p != null && !p.idQfInit) {
                        p.idQf = n.idQf
                        if (idTextQf) {
                            p.textQf = n.textQf
                        }
                        p = p.parent
                        p?.children?.forEach { bro ->
                            bro.idQf = n.idQf
                            if (idTextQf) {
                                bro.textQf = n.textQf
                            }
                        }
                    }
                } else {
                    val tempStack = mutableListOf(n)
                    while (tempStack.isNotEmpty()) {
                        val top = tempStack.removeAt(tempStack.lastIndex)
                        top.idQf = n.idQf
                        if (idTextQf) {
                            top.textQf = n.textQf
                        }
                        repeat(top.children.size) { i ->
                            tempStack.add(top.children[top.children.size - i - 1])
                        }
                    }
                }
            }

            if (!n.textQfInit && n.textQf != null) {
                n.parent?.children?.forEach { c ->
                    c.textQf = n.textQf
                    if (idTextQf) {
                        c.idQf = n.idQf
                    }
                }
                if (n.textQf == true) {
                    var p = n.parent
                    while (p != null && !p.textQfInit) {
                        p.textQf = n.textQf
                        if (idTextQf) {
                            p.idQf = n.idQf
                        }
                        p = p.parent
                        p?.children?.forEach { bro ->
                            bro.textQf = n.textQf
                            if (idTextQf) {
                                bro.idQf = bro.idQf
                            }
                        }
                    }
                } else {
                    val tempStack = mutableListOf(n)
                    while (tempStack.isNotEmpty()) {
                        val top = tempStack.removeAt(tempStack.lastIndex)
                        top.textQf = n.textQf
                        if (idTextQf) {
                            top.idQf = n.idQf
                        }
                        repeat(top.children.size) { i ->
                            tempStack.add(top.children[top.children.size - i - 1])
                        }
                    }
                }
            }

            n.idQfInit = true
            n.textQfInit = true
        }
        for (i in (nodes.size - 1) downTo 0) {
            val n = nodes[i]
            if (n.children.isEmpty()) {
                updateQf(n)
            }
        }
        for (i in (nodes.size - 1) downTo 0) {
            val n = nodes[i]
            if (n.children.isNotEmpty()) {
                updateQf(n)
            }
        }
    }


    return nodes.map { n ->
        NodeInfo(
            id = n.id,
            pid = n.parent?.id ?: -1,
            idQf = n.idQf,
            textQf = n.textQf,
            attr = n.attr
        )
    }
}

