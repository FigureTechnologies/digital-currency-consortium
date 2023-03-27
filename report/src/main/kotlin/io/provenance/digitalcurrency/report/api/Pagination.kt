package io.provenance.digitalcurrency.report.api

data class Page(
    val page: Int,
    val size: Int,
) {
    fun offset(): Long = (page - 1) * size.toLong()
}

data class Pagination(
    val page: Int,
    val size: Int,
    val totalCount: Long,
)

data class PaginationResponse(
    val page: Int,
    val size: Int,
    val pageCount: Int,
    val totalCount: Long,
)

data class PaginatedResponse<T>(
    val data: T,
    val pagination: PaginationResponse,
)
