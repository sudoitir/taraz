package io.github.sudoitir.taraz.core.domain.service;

import io.github.sudoitir.taraz.core.domain.account.Account;
import io.github.sudoitir.taraz.core.domain.transaction.Transaction;
import java.util.List;

/** The outcome of a posting: the transaction built, plus the accounts it mutated. */
public record PostingResult(Transaction transaction, List<Account> mutatedAccounts) {}
