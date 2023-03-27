package io.provenance.digitalcurrency.report.extension

import io.provenance.digitalcurrency.report.api.PaginatedResponse
import io.provenance.digitalcurrency.report.api.Pagination
import io.provenance.digitalcurrency.report.api.PaginationResponse
import kotlin.math.ceil

fun Pagination.toPaginatedResponse(pageSize: Int) = PaginationResponse(
    page = page,
    size = pageSize,
    pageCount = ceil(totalCount.toFloat() / size.toFloat()).toInt(),
    totalCount = totalCount,
)

fun <T> List<T>.toPaginatedResponse(pagination: Pagination) = PaginatedResponse(
    data = this,
    pagination = pagination.toPaginatedResponse(this.count()),
)
