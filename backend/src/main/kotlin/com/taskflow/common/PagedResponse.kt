package com.taskflow.common

data class PageMeta(
    val page: Int,
    val limit: Int,
    val totalItems: Long,
    val totalPages: Int,
)

fun pageMeta(page: Int, limit: Int, totalItems: Long): PageMeta {
    val totalPages = if (limit > 0) ((totalItems + limit - 1) / limit).toInt() else 0
    return PageMeta(page = page, limit = limit, totalItems = totalItems, totalPages = totalPages)
}

fun clampPagination(page: Int?, limit: Int?): Pair<Int, Int> {
    val p = (page ?: 1).coerceAtLeast(1)
    val l = (limit ?: 20).coerceIn(1, 100)
    return p to l
}
