package searchengine.controllers;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import searchengine.services.IndexingService;

import java.util.concurrent.atomic.AtomicBoolean;

@Controller
public class DefaultController {

    private static final Logger logger = LoggerFactory.getLogger(DefaultController.class);

    private final AtomicBoolean isIndexingInProgress = new AtomicBoolean(false);
    private final AtomicBoolean isFullyIndexed = new AtomicBoolean(false);

    private final IndexingService indexingService;

    @Autowired
    public DefaultController(IndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @GetMapping("/")
    public String index() {
        return "index";  // Возвращаем главную страницу
    }

    @GetMapping("/api/startIndexing")
    @ResponseBody
    public Response startIndexing() {
        // Проверка, что индексация уже не запущена
        if (isIndexingInProgress.get()) {
            return new Response(false, "Индексация уже запущена");
        }

        // Устанавливаем флаг начала индексации
        isIndexingInProgress.set(true);

        if (isFullyIndexed.get()) {
            logger.info("Запуск переиндексации...");
            startReindexingAsync();
        } else {
            logger.info("Запуск полной индексации...");
            startIndexingAsync();
        }

        return new Response(true, null);  // Индексация успешно запущена
    }

    @GetMapping("/api/stopIndexing")
    @ResponseBody
    public Response stopIndexing() {
        // Проверка, что индексация действительно запущена
        if (!isIndexingInProgress.get()) {
            return new Response(false, "Индексация не запущена");
        }

        // Останавливаем индексацию
        indexingService.stopIndexing();
        isIndexingInProgress.set(false);  // Сбрасываем флаг индексации

        logger.info("Индексация остановлена пользователем.");
        return new Response(true, null);  // Успешная остановка индексации
    }

    @Async
    public void startIndexingAsync() {
        try {
            logger.info("Начинаем полную индексацию сайтов...");
            indexingService.startIndexing();
            isFullyIndexed.set(true);  // Помечаем как полностью проиндексированный
        } catch (Exception e) {
            logger.error("Ошибка при индексации сайта", e);
        } finally {
            isIndexingInProgress.set(false);  // Сбрасываем флаг индексации
        }
    }

    @Async
    public void startReindexingAsync() {
        try {
            logger.info("Начинаем переиндексацию сайтов...");
            indexingService.reindexAll();
        } catch (Exception e) {
            logger.error("Ошибка при переиндексации сайта", e);
        } finally {
            isIndexingInProgress.set(false);  // Сбрасываем флаг индексации
        }
    }

    // Класс для формата ответа с использованием Lombok
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Response {
        private boolean result;
        private String error;
    }
}
