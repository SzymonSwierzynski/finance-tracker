package com.financetracker.account;

import com.financetracker.account.dto.AccountBalanceResponse;
import com.financetracker.account.dto.AccountResponse;
import com.financetracker.account.dto.CreateAccountRequest;
import com.financetracker.account.dto.UpdateAccountRequest;
import com.financetracker.common.error.ConflictException;
import com.financetracker.common.error.NotFoundException;
import com.financetracker.common.error.UnprocessableEntityException;
import com.financetracker.transaction.TransactionRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account business logic. Ownership is enforced here via user-scoped repository finders (a missing
 * or foreign id yields 404 — existence is never leaked). Updates are guarded by an optimistic-lock
 * version check (409 on stale writes).
 */
@Service
public class AccountService {

  private final AccountRepository accountRepository;
  private final TransactionRepository transactionRepository;

  public AccountService(
      AccountRepository accountRepository, TransactionRepository transactionRepository) {
    this.accountRepository = accountRepository;
    this.transactionRepository = transactionRepository;
  }

  @Transactional
  public AccountResponse create(long userId, CreateAccountRequest request) {
    boolean trackBalance = Boolean.TRUE.equals(request.trackBalance());
    Account account = new Account();
    account.setUserId(userId);
    account.setName(request.name().trim());
    account.setType(request.type());
    account.setCurrency(request.currency().toUpperCase(Locale.ROOT));
    account.setTrackBalance(trackBalance);
    // Starting balance is only meaningful when we track balance (matches the prototype).
    account.setStartingBalanceMinor(trackBalance ? request.startingBalanceMinor() : null);
    account.setArchived(false);
    return toResponse(accountRepository.save(account));
  }

  @Transactional(readOnly = true)
  public List<AccountResponse> list(long userId, boolean includeArchived) {
    List<Account> accounts =
        includeArchived
            ? accountRepository.findByUserIdOrderByNameAsc(userId)
            : accountRepository.findByUserIdAndArchivedFalseOrderByNameAsc(userId);
    return accounts.stream().map(AccountService::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public AccountResponse get(long userId, long id) {
    return toResponse(requireOwned(userId, id));
  }

  @Transactional
  public AccountResponse update(long userId, long id, UpdateAccountRequest request) {
    Account account = requireOwned(userId, id);
    requireCurrentVersion(account, request.version());

    if (request.name() != null) {
      account.setName(request.name().trim());
    }
    if (request.type() != null) {
      account.setType(request.type());
    }
    if (request.trackBalance() != null) {
      account.setTrackBalance(request.trackBalance());
    }
    if (request.startingBalanceMinor() != null) {
      account.setStartingBalanceMinor(request.startingBalanceMinor());
    }
    // Keep the invariant: a non-tracked account has no starting balance.
    if (!account.isTrackBalance()) {
      account.setStartingBalanceMinor(null);
    }
    // Flush so the response carries the incremented optimistic-lock version.
    return toResponse(accountRepository.saveAndFlush(account));
  }

  @Transactional
  public void archive(long userId, long id) {
    Account account = requireOwned(userId, id);
    account.setArchived(true);
    accountRepository.save(account);
  }

  @Transactional(readOnly = true)
  public AccountBalanceResponse balance(long userId, long id) {
    Account account = requireOwned(userId, id);
    if (!account.isTrackBalance()) {
      throw new UnprocessableEntityException("Balance tracking is not enabled for this account.");
    }
    long starting =
        account.getStartingBalanceMinor() == null ? 0L : account.getStartingBalanceMinor();
    long activity = transactionRepository.accountActivityMinor(userId, id);
    return new AccountBalanceResponse(id, account.getCurrency(), starting + activity);
  }

  private Account requireOwned(long userId, long id) {
    return accountRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> NotFoundException.of("Account", id));
  }

  private static void requireCurrentVersion(Account account, long expectedVersion) {
    if (account.getVersion() == null || account.getVersion() != expectedVersion) {
      throw new ConflictException("Account was modified concurrently; reload and retry.");
    }
  }

  private static AccountResponse toResponse(Account a) {
    return new AccountResponse(
        a.getId(),
        a.getName(),
        a.getType(),
        a.getCurrency(),
        a.getStartingBalanceMinor(),
        a.isTrackBalance(),
        a.isArchived(),
        a.getVersion());
  }
}
