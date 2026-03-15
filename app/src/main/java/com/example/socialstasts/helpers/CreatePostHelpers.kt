package com.example.socialstasts.helpers

import com.example.socialstasts.createpost.ACCOUNT_ALL
import com.example.socialstasts.persistance.AccountEntity

/**
 * Based on selected accounts, returns their names
 */
fun resolveTargetAccounts(accounts: List<AccountEntity>, selectedAccKeys: Set<String>): List<String> {
    val names = accounts.map { it.name }
    val isAll = selectedAccKeys.contains(ACCOUNT_ALL) ||
            (selectedAccKeys.isNotEmpty() && selectedAccKeys.containsAll(names.toSet()))

    return if (isAll) { names } else { names.filter { selectedAccKeys.contains(it) } }
}

fun getAccountsLabel(accounts: List<AccountEntity>, selectedKeys: Set<String>): String {
    if (accounts.isEmpty()) return "No accounts available"
    if (ACCOUNT_ALL in selectedKeys) return "ALL"

    val names = accounts.map { it.name }.filter { it in selectedKeys }
    return if (names.isEmpty()) "No account selected" else names.joinToString(", ")
}