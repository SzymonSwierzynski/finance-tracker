package com.financetracker.importing;

import com.financetracker.common.error.UnprocessableEntityException;
import com.financetracker.common.security.AuthUser;
import com.financetracker.common.security.CurrentUser;
import com.financetracker.importing.dto.CommitResponse;
import com.financetracker.importing.dto.ImportBatchResponse;
import com.financetracker.importing.dto.ImportMapping;
import com.financetracker.importing.dto.PreviewResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/imports")
@SecurityRequirement(name = "bearer-jwt")
public class ImportController {

  private final ImportService importService;

  public ImportController(ImportService importService) {
    this.importService = importService;
  }

  @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public PreviewResponse preview(
      @CurrentUser AuthUser user,
      @RequestParam long accountId,
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "mapping", required = false) @Valid ImportMapping mapping) {
    return importService.preview(user.id(), accountId, bytes(file), mapping);
  }

  @PostMapping(path = "/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<CommitResponse> commit(
      @CurrentUser AuthUser user,
      @RequestParam long accountId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "mapping", required = false) @Valid ImportMapping mapping) {
    String name = file.getOriginalFilename() == null ? "import.csv" : file.getOriginalFilename();
    CommitResponse result =
        importService.commit(user.id(), accountId, name, bytes(file), mapping, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  @GetMapping("/batches")
  public List<ImportBatchResponse> batches(@CurrentUser AuthUser user) {
    return importService.listBatches(user.id());
  }

  @DeleteMapping("/batches/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteBatch(@CurrentUser AuthUser user, @PathVariable long id) {
    importService.undoBatch(user.id(), id);
  }

  @GetMapping("/profiles/{accountId}")
  public ImportMapping getProfile(@CurrentUser AuthUser user, @PathVariable long accountId) {
    return importService.getProfile(user.id(), accountId);
  }

  @PutMapping("/profiles/{accountId}")
  public ImportMapping putProfile(
      @CurrentUser AuthUser user,
      @PathVariable long accountId,
      @Valid @RequestBody ImportMapping mapping) {
    return importService.saveProfile(user.id(), accountId, mapping);
  }

  private static byte[] bytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new UnprocessableEntityException("Could not read the uploaded file.");
    }
  }
}
