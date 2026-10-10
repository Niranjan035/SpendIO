package com.spendio.transactions.support;

import com.spendio.sharedkernel.web.PageRequestFactory;
import com.spendio.sharedkernel.web.SortRequest;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Whitelist-validated sort for the ledger (ARCHITECTURE.md E.6): occurredOn, amount,
 * description, account, category, createdAt. Default is newest-first with {@code id}
 * as the stable tiebreak; entity paths are translated to what JPA can order by.
 */
public final class TransactionSortSupport {

    public static final List<String> SORTABLE_FIELDS =
            List.of("occurredOn", "amount", "description", "account", "category", "createdAt");

    private TransactionSortSupport() {
    }

    public static Pageable pageable(Integer page, Integer size, String sort) {
        SortRequest parsed = SortRequest.parse(sort, SORTABLE_FIELDS);
        if (parsed.property() == null) {
            return PageRequestFactory.of(page, size,
                    Sort.by(Sort.Direction.DESC, "occurredOn").and(Sort.by(Sort.Direction.DESC, "id")));
        }
        Sort.Direction direction = parsed.direction();
        String path = switch (parsed.property()) {
            case "amount" -> "amountMinor";
            case "account" -> "account.name";
            case "category" -> "category.name";
            default -> parsed.property();
        };
        return PageRequestFactory.of(page, size, Sort.by(direction, path).and(Sort.by(direction, "id")));
    }
}
