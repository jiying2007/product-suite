package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartLayoutTest {
    @Test fun joinsFixedWidthChineseHardWrapsWithoutAddingSpaces() {
        val source = listOf(
            "夜色沿着旧城的屋檐一点点沉下来，街边的灯笼刚刚亮起",
            "远处有人推开木门，风把门上的铜铃吹得轻轻响了几声",
            "她抱着刚买来的书站在桥边，看河面把灯影拉成长长一线",
            "船夫从石阶下经过，没有抬头，只把竹篙稳稳压进水里",
            "城门方向传来晚钟，声音越过瓦顶，落进安静的小巷深处",
            "她这才合上书页，沿着熟悉的路慢慢往家里的方向走去。",
        ).joinToString("\n")

        val result = SmartLayout.present(source)
        assertTrue(result.hardWrapDetected)
        assertTrue(result.joinedBreaks >= 4)
        assertFalse(result.text.contains("亮起\n远处"))
        assertTrue(result.text.endsWith("走去。"))
    }

    @Test fun preservesNormalParagraphPerLineAndDialogue() {
        val source = "第一章 夜雨\n\n她推开窗。\n\n“你终于回来了。”\n\n院子里的雨还在下。"
        val result = SmartLayout.present(source)
        assertFalse(result.hardWrapDetected)
        assertEquals(source, result.text)
    }

    @Test fun preservesEnglishSentencePerLineAndMixedTxt() {
        val source = listOf(
            "The quick brown fox jumps over the lazy dog.",
            "This is a complete English sentence in a normal TXT file.",
            "Reader should preserve this newline instead of treating it as a hard wrap.",
            "Another ordinary sentence ends with a full stop.",
            "中英混排内容也可能逐句换行，而不是固定宽度强制折行。",
            "The final English sentence should remain on its own line.",
        ).joinToString("\n")

        val result = SmartLayout.present(source)
        assertFalse(result.hardWrapDetected)
        assertEquals(source, result.text)
    }

    @Test fun preservesIndentedParagraphBoundaryEvenInsideHardWrappedSample() {
        val source = listOf(
            "山路从村口一直向北延伸，清晨的雾还没有完全散开",
            "石阶旁的野草沾着水珠，鞋底踩过去会留下很浅的痕迹",
            "老人提着竹篮慢慢往前走，偶尔停下来听一听林中的鸟声",
            "树影随着风来回摇晃，细碎的光落在长满青苔的石墙上",
            "门前的木牌已经褪色，却还能看见当年留下来的几个旧字",
            "　　这是一个新的自然段，它不应该与上一行被智能拼接",
            "屋里传来烧水的声音，白汽很快从半开的窗缝里飘了出来。",
        ).joinToString("\n")
        val result = SmartLayout.present(source)
        assertTrue(result.text.contains("旧字\n　　这是一个新的自然段"))
    }

    @Test fun preservesListItemsInsideOtherwiseHardWrappedWindow() {
        val source = listOf(
            "城南旧仓库里堆着许多搬家留下的木箱，标签上的墨迹已经发白",
            "管理员把登记册摊在桌上，照着编号一件一件核对里面的东西",
            "窗边落着一层很薄的灰，午后的光线把纸页照得有些刺眼",
            "走廊尽头有人推来小车，轮子压过接缝时发出规律的轻响",
            "1、需要单独登记的旧书和手稿请放在靠门的长桌上等待确认",
            "下一批木箱从电梯里推出来，工作人员继续沿着标签向下检查",
            "墙上的时钟已经走过四点，仓库里仍然没有人准备提前离开",
            "等最后一张清单核完以后，他们才把卷帘门慢慢放了下来。",
        ).joinToString("\n")

        val result = SmartLayout.present(source)
        assertTrue(result.hardWrapDetected)
        assertTrue(result.text.contains("轻响\n1、需要单独登记"))
        assertTrue(result.text.contains("等待确认\n下一批木箱"))
    }

    @Test fun preservesSceneBreakAndSingleEmDashDialogueTurn() {
        val source = listOf(
            "雨从凌晨一直落到天亮，院子里的青石板被冲洗得发亮",
            "屋檐下的水珠连成细线，偶尔被风吹得斜斜落到台阶上",
            "她把最后一页信纸折好，放进抽屉最里面那只旧木盒里",
            "远处传来第一班电车的声音，城市开始慢慢从睡意中醒来",
            "天边刚透出一点灰白，街口的早餐铺已经点亮了第一盏灯",
            "***",
            "门外忽然响起脚步声，她还没来得及起身就听见有人敲门",
            "—我只是来把昨天借走的那本书还给你，马上就会离开这里",
            "她没有回答，只把窗帘拉开一点，看见天边终于亮了起来。",
        ).joinToString("\n")

        val result = SmartLayout.present(source)
        assertTrue(result.hardWrapDetected)
        assertTrue(result.text.contains("第一盏灯\n***\n门外"))
        assertTrue(result.text.contains("敲门\n—我只是来"))
    }

    @Test fun projectionRemainsMonotonicAcrossRemovedHardWraps() {
        val source = listOf(
            "旧书店门前摆着几张木桌，桌面上堆满刚从仓库搬出的书",
            "老板坐在最里面整理账本，听见脚步声才慢慢抬起头来",
            "窗外的阳光照进狭长过道，把空气里的灰尘照得清清楚楚",
            "她从书架抽出一本旧册，纸页边缘已经变成柔和的浅黄色",
            "扉页写着陌生人的名字，下面还有一行很淡的钢笔字迹",
            "她没有急着翻下去，只站在原地把那行字重新读了一遍。",
        ).joinToString("\n")
        val display = SmartLayout.present(source).text
        val projection = TextProjection.between(source, display)
        var last = 0L
        for (index in 0..display.codePointCount(0, display.length)) {
            val current = projection.sourceForDisplay(index.toLong())
            assertTrue(current >= last)
            last = current
        }
        assertEquals(source.codePointCount(0, source.length).toLong(), projection.sourceCodePoints)
    }
}
