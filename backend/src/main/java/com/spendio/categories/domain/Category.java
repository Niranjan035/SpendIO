package com.spendio.categories.domain;

import com.spendio.sharedkernel.domain.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "categories")
public class Category extends SoftDeletableEntity {

    public enum Type { INCOME, EXPENSE }

    public enum Status { ACTIVE, ARCHIVED }

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "name", length = 60, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private Type type;

    @Column(name = "icon", length = 40)
    private String icon;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "color", length = 7)
    private String color;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "is_system", nullable = false)
    private boolean isSystem;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    @Column(name = "exclude_from_budgets", nullable = false)
    private boolean excludeFromBudgets;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "archived_at")
    private java.time.Instant archivedAt;

    protected Category() {
    }

    public static Category create(Long userId, String name, Type type, Long parentId, String icon, String color, boolean excludeFromBudgets) {
        Category c = new Category();
        c.userId = userId;
        c.parentId = parentId;
        c.name = name;
        c.type = type;
        c.icon = icon;
        c.color = color;
        c.isDefault = false;
        c.isSystem = false;
        c.sortOrder = (short) 0;
        c.excludeFromBudgets = excludeFromBudgets;
        c.status = Status.ACTIVE;
        return c;
    }

    public void setName(String name) { this.name = name; }
    public void setIcon(String icon) { this.icon = icon; }
    public void setColor(String color) { this.color = color; }
    public void setExcludeFromBudgets(boolean excludeFromBudgets) { this.excludeFromBudgets = excludeFromBudgets; }
    public void setSortOrder(Short sortOrder) { this.sortOrder = sortOrder; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public void setStatus(Status status) { this.status = status; }
    public void setSystem(boolean system) { this.isSystem = system; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
    public void markArchived() { this.status = Status.ARCHIVED; this.archivedAt = java.time.Instant.now(); }
    public void markRestored() { this.status = Status.ACTIVE; this.archivedAt = null; }

    public Long getUserId() { return userId; }
    public Long getParentId() { return parentId; }
    public String getName() { return name; }
    public Type getType() { return type; }
    public String getIcon() { return icon; }
    public String getColor() { return color; }
    public boolean isDefault() { return isDefault; }
    public boolean isSystem() { return isSystem; }
    public Short getSortOrder() { return sortOrder; }
    public boolean isExcludeFromBudgets() { return excludeFromBudgets; }
    public Status getStatus() { return status; }
    public java.time.Instant getArchivedAt() { return archivedAt; }
}