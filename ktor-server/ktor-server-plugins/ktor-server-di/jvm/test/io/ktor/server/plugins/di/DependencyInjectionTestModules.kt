/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.application.*
import io.ktor.server.plugins.di.annotations.Property
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun createGreetingService(): GreetingService =
    GreetingServiceImpl()

internal fun createBankService(): BankService =
    BankServiceImpl()

internal suspend fun DependencyRegistry.createBankTellerNoArgs(): BankTeller =
    BankTeller(resolve(), resolve())

internal fun createBankTellerWithArgs(
    greetingService: GreetingService = BankGreetingService(),
    bankService: BankService
): BankTeller = BankTeller(greetingService, bankService)

internal suspend fun createBankTellerWithLogging(
    environment: ApplicationEnvironment,
    registry: DependencyRegistry,
): BankTeller {
    environment.log.info("Creating BankTeller with environment argument")
    return BankTeller(BankGreetingService(), registry.resolve())
}

internal fun createBankTellerWithNullables(
    greetingService: GreetingService?,
    bankService: BankService?
): BankTeller =
    BankTeller(
        greetingService ?: BankGreetingService(),
        bankService ?: BankServiceImpl()
    )

@Suppress("unused")
private fun getBankServicePrivately(): BankService =
    BankServiceImpl()

internal class BankModule {
    fun getBankServiceFromClass(): BankService =
        BankServiceImpl()
}

internal fun Application.bankingModule(
    greetingService: GreetingService,
    bankService: BankService
) {
    val teller = BankTeller(greetingService, bankService)
    routing {
        get("/hello") {
            call.respondText(teller.hello())
        }
    }
}

internal fun dataSource(@Property("database") config: ConnectionConfig, logger: FakeLogger) =
    DataSourceImpl(config, logger)

internal fun Application.restModule(dataSource: DataSource) {
    routing {
        get("/data") {
            dataSource.connect()
            call.respond("Connected")
        }
    }
}
