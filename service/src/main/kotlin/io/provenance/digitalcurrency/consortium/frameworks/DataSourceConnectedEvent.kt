package io.provenance.digitalcurrency.consortium.frameworks

import org.springframework.context.ApplicationEvent

class DataSourceConnectedEvent(source: Any, val message: String) : ApplicationEvent(source)
