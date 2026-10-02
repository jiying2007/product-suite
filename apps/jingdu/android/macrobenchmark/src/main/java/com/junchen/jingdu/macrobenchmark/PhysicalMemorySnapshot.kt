package com.junchen.jingdu.macrobenchmark

internal data class PhysicalMemorySnapshot(
    val totalPssKb: Long,
    val javaHeapKb: Long,
    val nativeHeapKb: Long,
    val graphicsKb: Long,
) {
    init {
        require(totalPssKb > 0L) { "TOTAL PSS unavailable" }
        require(javaHeapKb >= 0L) { "Java Heap PSS unavailable" }
        require(nativeHeapKb >= 0L) { "Native Heap PSS unavailable" }
        require(graphicsKb >= 0L) { "Graphics PSS unavailable" }
    }

    companion object {
        fun parse(meminfo: String): PhysicalMemorySnapshot {
            fun appSummary(label: String): Long =
                Regex("""(?m)^\s*${Regex.escape(label)}:\s+(\d+)""")
                    .find(meminfo)
                    ?.groupValues
                    ?.get(1)
                    ?.toLongOrNull()
                    ?: -1L

            val totalPss = Regex("""TOTAL PSS:\s+(\d+)""")
                .find(meminfo)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
                ?: Regex("""(?m)^\s*TOTAL\s+(\d+)""")
                    .find(meminfo)
                    ?.groupValues
                    ?.get(1)
                    ?.toLongOrNull()
                ?: 0L

            return PhysicalMemorySnapshot(
                totalPssKb = totalPss,
                javaHeapKb = appSummary("Java Heap"),
                nativeHeapKb = appSummary("Native Heap"),
                graphicsKb = appSummary("Graphics"),
            )
        }
    }
}
