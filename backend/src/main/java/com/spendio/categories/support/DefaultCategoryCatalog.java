package com.spendio.categories.support;

import com.spendio.categories.domain.Category;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DefaultCategoryCatalog {
  public record Item(String name, Category.Type type, Long parentId, String icon, String color, boolean excludeFromBudgets) {}

  public List<Item> expenseChildren(String parent) { return List.of(); }
  public List<Item> incomeChildren(String parent) { return List.of(); }

  public List<Item> getExpenseRoots() {
    return List.of(
      new Item("Food & Groceries", Category.Type.EXPENSE, null, "utensils", "#16a34a", false),
      new Item("Transport", Category.Type.EXPENSE, null, "car", "#3b82f6", false),
      new Item("Bills & Utilities", Category.Type.EXPENSE, null, "receipt", "#f59e0b", false),
      new Item("Entertainment", Category.Type.EXPENSE, null, "film", "#a855f7", false),
      new Item("Health & Fitness", Category.Type.EXPENSE, null, "heart", "#ef4444", false),
      new Item("Shopping", Category.Type.EXPENSE, null, "shopping-bag", "#06b6d4", false),
      new Item("Personal", Category.Type.EXPENSE, null, "user", "#8b5cf6", false),
      new Item("Subscriptions", Category.Type.EXPENSE, null, "refresh-cw", "#6366f1", false),
      new Item("Education", Category.Type.EXPENSE, null, "book", "#14b8a6", false),
      new Item("Travel", Category.Type.EXPENSE, null, "plane", "#0ea5e9", false),
      new Item("Gifts & Donations", Category.Type.EXPENSE, null, "gift", "#f97316", false),
      new Item("Fees & Charges", Category.Type.EXPENSE, null, "alert-circle", "#64748b", false),
      new Item("Taxes", Category.Type.EXPENSE, null, "file-text", "#475569", false),
      new Item("Other Expense", Category.Type.EXPENSE, null, "more-horizontal", "#94a3b8", false)
    );
  }

  public List<Item> getIncomeRoots() {
    return List.of(
      new Item("Salary", Category.Type.INCOME, null, "briefcase", "#22c55e", true),
      new Item("Freelance", Category.Type.INCOME, null, "code", "#10b981", true),
      new Item("Investments", Category.Type.INCOME, null, "trending-up", "#06b6d4", true),
      new Item("Gifts Received", Category.Type.INCOME, null, "gift", "#a3e635", true),
      new Item("Other Income", Category.Type.INCOME, null, "dollar-sign", "#65a30d", true)
    );
  }

  public List<Item> getAll() {
    List<Item> all = new ArrayList<>();
    all.addAll(getExpenseRoots());
    all.addAll(getIncomeRoots());
    return all;
  }
}