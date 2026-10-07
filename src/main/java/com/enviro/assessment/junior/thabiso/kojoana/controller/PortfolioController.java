package com.enviro.assessment.junior.thabiso.kojoana.controller;

import com.enviro.assessment.junior.thabiso.kojoana.dto.*;
import com.enviro.assessment.junior.thabiso.kojoana.exception.NotFoundException;
import com.enviro.assessment.junior.thabiso.kojoana.exception.ValidationException;
import com.enviro.assessment.junior.thabiso.kojoana.service.PortfolioService;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/investors")
// The /api/investors endpoints call PortfolioService and return its results as JSON or CSV.
public class PortfolioController {
  private final PortfolioService service;

  public PortfolioController(PortfolioService service) {
    this.service = service;
  }

  @GetMapping
  public List<InvestorDto> investors() {
    return service.listInvestors();
  }

  @GetMapping("/{id}/portfolio")
  public PortfolioDto portfolio(@PathVariable Long id) {
    return service.portfolio(id);
  }

  // The Location header gives the URL where the saved notice can be retrieved.
  @PostMapping("/{id}/withdrawals")
  public ResponseEntity<WithdrawalDto> withdraw(
      @PathVariable Long id, @Valid @RequestBody WithdrawalRequest request) {
    WithdrawalDto notice = service.createWithdrawal(id, request);
    return ResponseEntity.created(
            URI.create("/api/investors/" + id + "/withdrawals/" + notice.id()))
        .body(notice);
  }

  @GetMapping("/{id}/withdrawals/{noticeId}")
  public WithdrawalDto notice(@PathVariable Long id, @PathVariable Long noticeId) {
    return service.withdrawal(id, noticeId);
  }

  @GetMapping("/{id}/withdrawals")
  public List<WithdrawalDto> history(
      @PathVariable Long id,
      @RequestParam(required = false) Long productId,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    return service.history(id, productId, from, to);
  }

  // The CSV uses the history filters, so it contains the same matching withdrawals.
  @GetMapping("/{id}/withdrawals/export")
  public ResponseEntity<byte[]> export(
      @PathVariable Long id,
      @RequestParam(required = false) Long productId,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    byte[] csv =
        service.csv(service.history(id, productId, from, to)).getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=withdrawals-" + id + ".csv")
        .body(csv);
  }

  // These error responses apply to PortfolioController and include messages the page can display.
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorDto> notFound(NotFoundException error) {
    return ResponseEntity.status(404).body(new ErrorDto(error.getMessage(), Map.of()));
  }

  // The field name lets the page place this rule message beside the affected input.
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ErrorDto> invalidWithdrawal(ValidationException error) {
    return ResponseEntity.badRequest()
        .body(new ErrorDto(error.getMessage(), Map.of(error.getField(), error.getMessage())));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorDto> invalidFields(MethodArgumentNotValidException error) {
    Map<String, String> fields = new LinkedHashMap<>();
    for (FieldError field : error.getBindingResult().getFieldErrors()) {
      fields.putIfAbsent(field.getField(), field.getDefaultMessage());
    }
    return ResponseEntity.badRequest().body(new ErrorDto("Check the highlighted fields.", fields));
  }

  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class
  })
  public ResponseEntity<ErrorDto> invalidRequest(Exception error) {
    return ResponseEntity.badRequest()
        .body(new ErrorDto("Check the request fields and dates (YYYY-MM-DD).", Map.of()));
  }

  // A second withdrawal may need to wait while the first updates the balance.
  @ExceptionHandler(PessimisticLockingFailureException.class)
  public ResponseEntity<ErrorDto> balanceBusy(PessimisticLockingFailureException error) {
    return ResponseEntity.status(409)
        .body(new ErrorDto("This balance is being updated. Refresh and try again.", Map.of()));
  }
}
