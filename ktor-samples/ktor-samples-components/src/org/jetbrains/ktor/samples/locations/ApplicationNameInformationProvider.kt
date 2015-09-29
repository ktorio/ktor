package org.jetbrains.ktor.samples.locations

import org.jetbrains.ktor.components.*

@Component
public class ApplicationNameInformationProvider : InformationProvider {
    override fun information(): Information {
        return Information("ApplicationName", "Component Sample")
    }
}