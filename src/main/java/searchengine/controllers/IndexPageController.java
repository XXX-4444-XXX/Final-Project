package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.services.IndexingPageService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class IndexPageController {

    private final IndexingPageService indexingPageService;

    // Эндпоинт для индексации страницы
    @PostMapping("/indexPage")
    public ResponseEntity<Object> indexPage(@RequestBody String url) {
        // Проверка, что URL не пустой или null
        if (url == null || url.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("URL не передан"));
        }

        // Обрабатываем запрос на индексацию
        Object response = indexingPageService.indexPage(url);

        // Если ответ успешный, возвращаем статус OK с результатом
        if (response instanceof SuccessResponse) {
            return ResponseEntity.ok(response);
        } else {
            // Если ошибка, возвращаем 400 с сообщением ошибки
            return ResponseEntity.badRequest().body(response);
        }
    }

    // Эндпоинт для проверки статуса индексации страницы
    @GetMapping("/status")
    public ResponseEntity<Object> getStatus() {
        // Простой статус ответа, можно расширить логику
        return ResponseEntity.ok(new SuccessResponse("Система индексации работает"));
    }

    // Ошибки ответа
    @lombok.Getter
    @lombok.Setter
    static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
            this.error = error;
        }
    }

    // Ответ в случае успеха
    @lombok.Getter
    @lombok.Setter
    static class SuccessResponse {
        private boolean result;
        private String message;

        public SuccessResponse(boolean result) {
            this.result = result;
        }

        public SuccessResponse(String message) {
            this.message = message;
        }
    }
}
