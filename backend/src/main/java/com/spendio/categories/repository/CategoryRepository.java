package com.spendio.categories.repository;

import com.spendio.categories.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long>, JpaSpecificationExecutor<Category> {
  Optional<Category> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
  List<Category> findByUserIdAndDeletedAtIsNull(Long userId);
  List<Category> findByUserIdAndParentIdIsNullAndDeletedAtIsNullAndStatusOrderBySortOrderAsc(Long userId, Category.Status status);
  List<Category> findByUserIdAndParentIdAndDeletedAtIsNullAndStatusOrderBySortOrderAsc(Long userId, Long parentId, Category.Status status);
  List<Category> findByUserIdAndDeletedAtIsNullAndStatusOrderBySortOrderAsc(Long userId, Category.Status status);
  List<Category> findByUserIdAndTypeAndDeletedAtIsNullAndStatusOrderBySortOrderAsc(Long userId, Category.Type type, Category.Status status);
  boolean existsByUserIdAndParentIdAndNameAndDeletedAtIsNull(Long userId, Long parentId, String name);
  boolean existsByUserIdAndParentIdAndNameAndDeletedAtIsNullAndIdNot(Long userId, Long parentId, String name, Long id);
  long countByUserIdAndParentIdAndDeletedAtIsNull(Long userId, Long parentId);
  @Query("select c from Category c where c.userId = :userId and c.deletedAt is null")
  List<Category> findAllActiveForUser(@Param("userId") Long userId);
}