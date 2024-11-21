package searchengine.services;

import lombok.Getter;
import lombok.Setter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IndexingPageService {

    // Разрешенные домены
    private static final List<String> ALLOWED_DOMAINS = Arrays.asList("example.com", "mywebsite.com");

    private static final Logger logger = LoggerFactory.getLogger(IndexingPageService.class);

    private final JdbcTemplate jdbcTemplate;

    public IndexingPageService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Метод для индексации страницы
    public Object indexPage(String url) {
        // Проверка на корректность URL
        if (!isValidUrl(url)) {
            logger.error("Неверный формат URL: {}", url);
            return new ErrorResponse("Неверный формат URL");
        }

        // Проверка на принадлежность разрешенным доменам
        if (!isAllowedDomain(url)) {
            logger.error("Данная страница находится за пределами сайтов, указанных в конфигурационном файле: {}", url);
            return new ErrorResponse("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }

        // Получение HTML-кода страницы
        String html = fetchHtml(url);
        if (html == null) {
            logger.error("Не удалось загрузить страницу: {}", url);
            return new ErrorResponse("Не удалось загрузить страницу");
        }

        // Индексация страницы
        return indexHtml(url, html);
    }

    // Метод для извлечения HTML с URL
    private String fetchHtml(String url) {
        try {
            // Используем Jsoup для извлечения HTML
            Document doc = Jsoup.connect(url).get();
            return doc.html();
        } catch (Exception e) {
            logger.error(String.format("Ошибка при извлечении HTML с URL %s", url), e);
            return null;
        }
    }

    // Метод индексации HTML
    private Object indexHtml(String url, String html) {
        try {
            // Удаляем данные о странице, если она уже проиндексирована
            removePageData(url);

            // Сохраняем страницу в базу данных
            int pageId = savePage(url, html);
            if (pageId == -1) {
                logger.error("Ошибка при сохранении страницы в базу данных для URL: {}", url);
                return new ErrorResponse("Ошибка при сохранении страницы в базу данных");
            }

            // Извлекаем леммы из HTML-кода
            List<String> lemmas = extractLemmasFromHtml(html);

            // Подсчитываем частоту каждой леммы
            Map<String, Integer> lemmaCounts = new HashMap<>();
            for (String lemma : lemmas) {
                lemmaCounts.put(lemma, lemmaCounts.getOrDefault(lemma, 0) + 1);
            }

            // Сохраняем леммы и их связи с текущей страницей
            for (Map.Entry<String, Integer> entry : lemmaCounts.entrySet()) {
                String lemma = entry.getKey();
                int rank = entry.getValue();

                // Сохраняем лемму или обновляем её частоту
                int lemmaId = saveLemma(lemma);

                // Сохраняем связь леммы с страницей
                saveIndex(pageId, lemmaId, rank);
            }

            // Успешная индексация
            return new SuccessResponse(true);
        } catch (Exception e) {
            logger.error("Ошибка при индексации страницы", e);
            return new ErrorResponse("Ошибка при индексации страницы");
        }
    }


    private void removePageData(String url) {
        try {
            // Находим страницу в таблице page
            String selectPageIdSQL = "SELECT id FROM page WHERE url = ?";
            Integer pageId = jdbcTemplate.queryForObject(selectPageIdSQL, Integer.class, url);

            if (pageId != null) {
                // Удаляем связи в таблице index
                String deleteIndexSQL = "DELETE FROM index WHERE page_id = ?";
                jdbcTemplate.update(deleteIndexSQL, pageId);

                // Удаляем леммы, которые больше не связаны с другими страницами
                String deleteUnusedLemmasSQL =
                        "DELETE FROM lemma WHERE id NOT IN (SELECT DISTINCT lemma_id FROM index)";
                jdbcTemplate.update(deleteUnusedLemmasSQL);

                // Удаляем страницу из таблицы page
                String deletePageSQL = "DELETE FROM page WHERE id = ?";
                jdbcTemplate.update(deletePageSQL, pageId);

                logger.info("Удалена информация о странице с URL: {}", url);
            } else {
                logger.info("Информация о странице с URL {} не найдена, пропускаем удаление", url);
            }
        } catch (Exception e) {
            logger.error("Ошибка при удалении данных о странице с URL: {}", url, e);
        }
    }



    // Извлечение лемм из HTML-кода
    private List<String> extractLemmasFromHtml(String html) {
        // Используем Jsoup для парсинга HTML
        Document doc = Jsoup.parse(html);
        String text = doc.text();

        // Преобразуем текст в нижний регистр и разбиваем на слова
        String[] words = text.toLowerCase().split("\\W+");

        // Преобразуем в список лемм (здесь можно использовать NLP для реальной лемматизации)
        return Arrays.asList(words);
    }

    // Сохраняем страницу в базу данных
    private int savePage(String url, String html) {
        String domain = getDomain(url);

        // Получаем site_id из таблицы site
        String selectSiteSQL = "SELECT id FROM site WHERE url = ?";
        Integer siteId = jdbcTemplate.queryForObject(selectSiteSQL, Integer.class, domain);

        if (siteId == null) {
            logger.error("Сайт для домена {} не найден в таблице site", domain);
            return -1; // Ошибка: сайт не найден
        }

        // Сохраняем страницу в таблицу page
        String insertPageSQL = "INSERT INTO page (site_id, url, html) VALUES (?, ?, ?) RETURNING id";
        try {
            Integer result = jdbcTemplate.queryForObject(insertPageSQL, Integer.class, siteId, url, html);
            return result != null ? result : -1;
        } catch (Exception e) {
            logger.error("Ошибка при сохранении страницы в базе данных: {}", url, e);
            return -1;
        }
    }


    // Сохраняем лемму в таблицу lemma или обновляем её частоту
    private int saveLemma(String lemma) {
        String insertLemmaSQL = "INSERT INTO lemma (word) VALUES (?) ON CONFLICT (word) DO UPDATE SET frequency = frequency + 1 RETURNING id";
        // Обрабатываем возможность возвращения null (если лемма не добавлена)
        try {
            Integer result = jdbcTemplate.queryForObject(insertLemmaSQL, Integer.class, lemma);
            return result != null ? result : -1;
        } catch (Exception e) {
            logger.error("Ошибка при сохранении леммы в базе данных: {}", lemma, e);
            return -1;
        }
    }

    // Сохраняем связь между леммой и страницей в таблицу index
    private void saveIndex(int pageId, int lemmaId, int rank) {
        String checkExistenceSQL = "SELECT COUNT(*) FROM index WHERE page_id = ? AND lemma_id = ?";
        try {
            Integer count = jdbcTemplate.queryForObject(checkExistenceSQL, Integer.class, pageId, lemmaId);

            // Обработка, если count == null
            if (count == null || count == 0) {
                String insertIndexSQL = "INSERT INTO index (page_id, lemma_id, rank) VALUES (?, ?, ?)";
                jdbcTemplate.update(insertIndexSQL, pageId, lemmaId, rank);
            } else {
                String updateIndexSQL = "UPDATE index SET rank = ? WHERE page_id = ? AND lemma_id = ?";
                jdbcTemplate.update(updateIndexSQL, rank, pageId, lemmaId);
            }
        } catch (Exception e) {
            logger.error("Ошибка при сохранении индекса в базе данных для страницы с id: {} и леммы с id: {}", pageId, lemmaId, e);
        }
    }

    // Проверка на корректность URL
    private boolean isValidUrl(String url) {
        String regex = "^(https?://)?([a-z0-9-]+\\.)+[a-z0-9]{2,4}(:[0-9]+)?(/.*)?$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(url);
        return matcher.matches();
    }

    // Проверка на разрешенный домен
    private boolean isAllowedDomain(String url) {
        String domain = getDomain(url);
        return ALLOWED_DOMAINS.contains(domain);
    }

    // Извлечение домена из URL
    private String getDomain(String url) {
        try {
            String[] parts = url.split("/");
            String domainWithPort = parts[2];
            String domain = domainWithPort.split(":")[0];
            return domain.split("\\?")[0];
        } catch (Exception e) {
            return "";
        }
    }

    // Ответ в случае успеха
    @Getter
    @Setter
    static class SuccessResponse {
        private boolean result;

        public SuccessResponse(boolean result) {
            this.result = result;
        }
    }

    // Ошибка ответа
    @Getter
    @Setter
    static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
            this.error = error;
        }
    }
}
