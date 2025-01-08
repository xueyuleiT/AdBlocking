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
package li.songe.selector

import junit.framework.TestCase.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import li.songe.selector.parser.ParserSet
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.test.Test


class ParserTest {
    private val projectCwd = File("../").absolutePath
    private val assetsDir = File("$projectCwd/_assets").apply {
        if (!exists()) {
            mkdir()
        }
    }
    private val json = Json {
        ignoreUnknownKeys = true
    }

    private fun getNodeAttr(node: TestNode, name: String): Any? {
        if (name == "_id") return node.id
        if (name == "_pid") return node.pid
        if (name == "parent") return node.parent
        val value = node.attr[name] ?: return null
        if (value is JsonNull) return null
        return value.intOrNull ?: value.booleanOrNull ?: value.content
    }

    private fun getNodeInvoke(target: TestNode, name: String, args: List<Any>): Any? {
        when (name) {
            "getChild" -> {
                val arg = args.getInt()
                return target.children.getOrNull(arg)
            }
        }
        return null
    }

    private val transform = Transform<TestNode>(
        getAttr = { target, name ->
            when (target) {
                is Context<*> -> when (name) {
                    "prev" -> target.prev
                    "current" -> target.current
                    else -> getNodeAttr(target.current as TestNode, name)
                }

                is TestNode -> getNodeAttr(target, name)
                is String -> getCharSequenceAttr(target, name)

                else -> null
            }
        },
        getInvoke = { target, name, args ->
            when (target) {
                is Boolean -> getBooleanInvoke(target, name, args)
                is Int -> getIntInvoke(target, name, args)
                is CharSequence -> getCharSequenceInvoke(target, name, args)
                is TestNode -> getNodeInvoke(target, name, args)
                is Context<*> -> when (name) {
                    "getPrev" -> {
                        args.getInt().let { target.getPrev(it) }
                    }

                    else -> getNodeInvoke(target.current as TestNode, name, args)
                }

                else -> null
            }
        },
        getName = { node -> node.attr["name"]?.content },
        getChildren = { node -> node.children.asSequence() },
        getParent = { node -> node.parent }
    )

    private val idToSnapshot = HashMap<String, TestNode>()

    private fun getOrDownloadNode(url: String): TestNode {
        val githubAssetId = url.split('/').last()
        idToSnapshot[githubAssetId]?.let { return it }

        val file = assetsDir.resolve("$githubAssetId.json")
        if (!file.exists()) {
            URL("https://f.gkd.li/${githubAssetId}").openStream()
                .use { inputStream ->
                    val zipInputStream = ZipInputStream(inputStream)
                    var entry = zipInputStream.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".json")) {
                            val outputStream = BufferedOutputStream(FileOutputStream(file))
                            val buffer = ByteArray(1024)
                            var bytesRead: Int
                            while (zipInputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                            }
                            outputStream.close()
                            break
                        }
                        entry = zipInputStream.nextEntry
                    }
                    zipInputStream.closeEntry()
                    zipInputStream.close()
                }
        }
        val nodes = json.decodeFromString<TestSnapshot>(file.readText()).nodes

        nodes.forEach { node ->
            node.parent = nodes.getOrNull(node.pid)
            node.parent?.apply {
                children.add(node)
            }
        }
        return nodes.first().apply {
            idToSnapshot[githubAssetId] = this
        }
    }

    @Test
    fun test_expression() {
        println(ParserSet.expressionParser("a>1&&b>1&&c>1||d>1", 0))
        println(Selector.parse("View[a>1&&b>1&&c>1||d>1&&x^=1] > Button[a>1||b*='zz'||c^=1]"))
        println(Selector.parse("[id=`com.byted.pangle:id/tt_splash_skip_btn`||(id=`com.hupu.games:id/tv_time`&&text*=`跳过`)]"))
    }

    @Test
    fun string_selector() {
        val text =
            "ImageView < @FrameLayout < LinearLayout < RelativeLayout <n LinearLayout < RelativeLayout + LinearLayout > RelativeLayout > TextView[text\$='广告']"
        val selector = Selector.parse(text)
        println("trackIndex: " + selector.targetIndex)
        println("canCacheIndex: " + Selector.parse("A + B").useCache)
        println("canCacheIndex: " + Selector.parse("A > B - C").useCache)
    }

    @Test
    fun query_selector() {
        val text =
            "@[vid=\"rv_home_tab\"] <<(99-n) [vid=\"header_container\"] -(-2n+9) [vid=\"layout_refresh\"] +2 [vid=\"home_v10_frag_content\"]"
        val selector = Selector.parse(text)
        println("selector: $selector")
        val node = getOrDownloadNode("https://i.gkd.li/i/14325747")
        val targets = transform.querySelectorAll(node, selector).toList()
        println("target_size: " + targets.size)
        println("target_id: " + targets.map { t -> t.id })
        assertTrue(targets.size == 1)
        println("id: " + targets.first().id)

        val trackTargets = transform.querySelectorAllContext(node, selector).toList()
        println("trackTargets_size: " + trackTargets.size)
        assertTrue(trackTargets.size == 1)
        println(trackTargets.first())
    }

    @Test
    fun check_parser() {
        val selector = Selector.parse("View > Text[index>-0]")
        println("selector: $selector")
        println("canCacheIndex: " + selector.useCache)
    }


    @Test
    fun check_query() {
        val text =
            "@TextView[getPrev(0).text=`签到提醒`] - [text=`签到提醒`] <<n [vid=`webViewContainer`]"
        val selector = Selector.parse(text)
        println("selector: $selector")
        println(selector.targetIndex)

        val node = getOrDownloadNode("https://i.gkd.li/i/14384152")
        val targets = transform.querySelectorAll(node, selector).toList()
        println("target_size: " + targets.size)
        println(targets.firstOrNull())
    }

    @Test
    fun check_quote() {
//        https://github.com/gkd-kit/inspect/issues/7
        val selector = Selector.parse("a[a='\\\\'] ")
        println("check_quote:$selector")
    }

    @Test
    fun check_escape() {
        val source =
            "[a='\\\"'][a=\"'\"][a=`\\x20\\n\\uD83D\\uDE04`][a=`\\x20`][a=\"`\u0020\"][a=`\\t\\n\\r\\b\\x00\\x09\\x1d`]"
        println("source:$source")
        val selector = Selector.parse(source)
        println("check_quote:$selector")
    }

    @Test
    fun check_tuple() {
        val source = "[_id=15] >(1,2,9) X + Z >(7+9n) *"
        println("source:$source")
        val selector = Selector.parse(source)
        println("check_quote:$selector")

        // 1->3, 3->21
        // 1,3->24
        val snapshotNode = getOrDownloadNode("https://i.gkd.li/i/13247733")
        val (x1, x2) = (1..6).toList().shuffled().subList(0, 2).sorted()
        val x1N =
            transform.querySelectorAll(snapshotNode, Selector.parse("[_id=15] >$x1 *")).count()
        val x2N =
            transform.querySelectorAll(snapshotNode, Selector.parse("[_id=15] >$x2 *")).count()
        val x12N = transform.querySelectorAll(snapshotNode, Selector.parse("[_id=15] >($x1,$x2) *"))
            .count()

        println("$x1->$x1N, $x2->$x2N, ($x1,$x2)->$x12N")
    }

    @Test
    fun check_descendant() {
        // ad_container 符合 quickFind, 目标节点 tt_splash_skip_btn 在其内部但不符合 quickFind
        val source =
            "@[id=\"com.byted.pangle.m:id/tt_splash_skip_btn\"] <<n [id=\"com.coolapk.market:id/ad_container\"]"
        println("source:$source")
        val selector = Selector.parse(source)
        println("selector:$selector")
        val snapshotNode = getOrDownloadNode("https://i.gkd.li/i/13247610")
        println("result:" + transform.querySelectorAll(snapshotNode, selector).map { n -> n.id }
            .toList())
    }

    @Test
    fun check_regex() {
        val source = "[vid=`im_cover`][top.more(319).not()=true]"
        println("source:$source")
        val selector = Selector.parse(source)
        val snapshotNode = getOrDownloadNode("https://i.gkd.li/i/14445410")
        val error = selector.checkType(typeInfo)
        if (error != null) {
            println("error:$error")
            return
        }
        println("selector:${selector.stringify()}")
        println("result:" + transform.querySelectorAll(snapshotNode, selector).map { n -> n.id }
            .toList())
    }

    @Test
    fun check_var() {
        val result = ParserSet.parseVariable("rem(3)", 0)
        println("result: $result")
        println("check_var: " + result.stringify())

        val selector = Selector.parse("[vid.get(-2)=`l`]")
        println("selector: $selector")

        val snapshotNode = getOrDownloadNode("https://i.gkd.li/i/14445410")
        println(
            "result: [" + transform.querySelectorAll(snapshotNode, selector)
                .joinToString("||") { "_id=${it.id}" } + "]"
        )
    }

    private val typeInfo by lazy { initDefaultTypeInfo(webField = true).globalType }

    @Test
    fun check_type() {
        val source =
            "[prev.getChild(0,0)=0][prev!=null&&visibleToUser=true&&equal(index, depth)=true][((parent.getChild(0,).getChild( (0), )=null) && (((2  >=  1)))) || (name=null && desc=null)]"
        val selector = Selector.parse(source)
        val error = selector.checkType(typeInfo)
        println("useCache: ${selector.useCache}")
        println("error: $error")
        println("error_message: ${error?.message}")
        println("check_type: ${selector.stringify()}")
    }

    @Test
    fun check_qf() {
        val source = "@UIView[clickable=true] -3 FlattenUIText[text=`a`||text=`b`||vid=`233`]"
        val selector = Selector.parse(source)
        println("fastQuery: ${selector.fastQueryList}")
        println("quickFind: ${selector.quickFindValue}")
    }
}
