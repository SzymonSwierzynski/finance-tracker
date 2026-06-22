package com.financetracker.category;

import com.financetracker.category.dto.CategoryResponse;
import com.financetracker.category.dto.CreateCategoryRequest;
import com.financetracker.category.dto.DeleteCategoryResponse;
import com.financetracker.category.dto.UpdateCategoryRequest;
import com.financetracker.common.security.AuthUser;
import com.financetracker.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
@SecurityRequirement(name = "bearer-jwt")
public class CategoryController {

  private final CategoryService categoryService;

  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  @GetMapping
  public List<CategoryResponse> list(
      @CurrentUser AuthUser user, @RequestParam(required = false) CategoryKind kind) {
    return categoryService.list(user.id(), kind);
  }

  @PostMapping
  public ResponseEntity<CategoryResponse> create(
      @CurrentUser AuthUser user, @Valid @RequestBody CreateCategoryRequest request) {
    CategoryResponse created = categoryService.create(user.id(), request);
    return ResponseEntity.created(URI.create("/api/v1/categories/" + created.id())).body(created);
  }

  @PatchMapping("/{id}")
  public CategoryResponse update(
      @CurrentUser AuthUser user,
      @PathVariable long id,
      @Valid @RequestBody UpdateCategoryRequest request) {
    return categoryService.update(user.id(), id, request);
  }

  @DeleteMapping("/{id}")
  public DeleteCategoryResponse delete(@CurrentUser AuthUser user, @PathVariable long id) {
    return categoryService.delete(user.id(), id);
  }
}
