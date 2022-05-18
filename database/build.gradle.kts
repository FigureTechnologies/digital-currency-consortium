plugins {
    id(PluginIds.Flyway) version PluginVersions.Flyway
}

apply {
    plugin(PluginIds.Flyway)
}

dependencies {
    implementation(Libraries.Flyway)
}

flyway {
    url = "jdbc:postgresql://127.0.0.1:5432/digital_currency_consortium"
    driver = "org.postgresql.Driver"
    user = "postgres"
    password = "password1"
    schemas = arrayOf("digital_currency_consortium")
    locations = arrayOf("filesystem:$projectDir/src/main/resources/db/migration")
    validateOnMigrate = false
    outOfOrder = false
}
