package searchengine.controllers;

import searchengine.dto.statistics.SearchResult;
import searchengine.dto.statistics.SearchResponse;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class SearchController {

    // Пример базы данных индекса
    private static final List<SearchResult> indexData = new ArrayList<>();

    // Список стоп-слов (можно расширять)
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "и", "на", "к", "с", "по", "в", "не", "да", "так", "ли", "же", "для", "о", "от", "для", "это",
            "бы", "или", "как", "также", "потому", "что", "перед", "под", "над", "за", "из", "для", "находится", "чтобы"
    ));

    static {
        // Инициализация примера данных
        indexData.add(new SearchResult("http://www.site1.com", "Site 1", "/path/to/page1", "Page 1 Title", "This is the <b>first</b> page snippet", 0.95));
        indexData.add(new SearchResult("http://www.site2.com", "Site 2", "/path/to/page2", "Page 2 Title", "This is the <b>second</b> page snippet", 0.87));
    }

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String site,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {

        // Проверка на пустой запрос
        if (query == null || query.trim().isEmpty()) {
            return new SearchResponse(false, "Задан пустой поисковый запрос", 0, Collections.emptyList());
        }

        // Разбиваем запрос на слова, преобразуем их в леммы и исключаем стоп-слова
        Set<String> lemmas = processQuery(query);

        // Если нет валидных лемм
        if (lemmas.isEmpty()) {
            return new SearchResponse(false, "По вашему запросу ничего не найдено", 0, Collections.emptyList());
        }

        // Рассчитываем частоту лемм в индексе
        Map<String, Double> lemmaFrequency = calculateLemmaFrequency();

        // Исключаем леммы, которые встречаются на слишком большом числе страниц (например, более 50%)
        double threshold = 0.5;  // Леммы, которые встречаются более чем на 50% страниц
        Set<String> filteredLemmas = lemmas.stream()
                .filter(lemma -> lemmaFrequency.getOrDefault(lemma, 0.0) <= threshold)
                .collect(Collectors.toSet());

        // Если нет валидных лемм после фильтрации
        if (filteredLemmas.isEmpty()) {
            return new SearchResponse(false, "По вашему запросу ничего не найдено", 0, Collections.emptyList());
        }

        // Проверка на отсутствие индекса для указанного сайта
        if (site != null && indexData.stream().noneMatch(result -> result.getSite().equals(site))) {
            return new SearchResponse(false, "Указанный сайт не найден в индексе", 0, Collections.emptyList());
        }

        // Сортируем леммы по частоте (по возрастанию)
        List<String> sortedLemmas = filteredLemmas.stream()
                .sorted(Comparator.comparingDouble(lemmaFrequency::get))
                .toList();

        // Начальный список всех страниц
        Set<SearchResult> filteredResults = new HashSet<>(indexData);

        // Итерация по леммам, фильтрация результатов
        for (String lemma : sortedLemmas) {
            filteredResults = filteredResults.stream()
                    .filter(result -> containsAnyLemma(result.getTitle(), Set.of(lemma)) ||
                            containsAnyLemma(result.getSnippet(), Set.of(lemma)))
                    .collect(Collectors.toSet());

            // Если на текущем шаге нет результатов, завершаем поиск
            if (filteredResults.isEmpty()) {
                return new SearchResponse(true, null, 0, Collections.emptyList()); // Пустой список, если нет результатов
            }
        }

        // Рассчитываем релевантность для каждой страницы
        List<SearchResultWithRelevance> relevantResults = new ArrayList<>();
        for (SearchResult result : filteredResults) {
            // Собираем леммы, встречающиеся на странице (в заголовке и сниппете)
            Set<String> resultLemmas = new HashSet<>(processQuery(result.getTitle()));
            resultLemmas.addAll(processQuery(result.getSnippet()));  // Дополнительно учитываем сниппет

            // Рассчитываем абсолютную релевантность
            double absoluteRelevance = resultLemmas.stream()
                    .mapToDouble(this::getRankForLemma) // Получаем rank для каждой леммы
                    .sum();

            if (absoluteRelevance > 0) {
                relevantResults.add(new SearchResultWithRelevance(result, absoluteRelevance, generateSnippet(result, filteredLemmas)));
            }
        }

        // Находим максимальную абсолютную релевантность среди всех страниц
        double maxAbsoluteRelevance = relevantResults.stream()
                .mapToDouble(SearchResultWithRelevance::absoluteRelevance)
                .max()
                .orElse(1);

        // Рассчитываем относительную релевантность и сортируем по убыванию
        relevantResults.sort(Comparator.comparingDouble((SearchResultWithRelevance r) ->
                r.absoluteRelevance() / maxAbsoluteRelevance).reversed());

        // Пагинация
        int totalCount = relevantResults.size();
        List<SearchResult> paginatedResults = relevantResults.stream()
                .skip(offset)
                .limit(limit)
                .map(SearchResultWithRelevance::searchResult)
                .collect(Collectors.toList());

        return new SearchResponse(true, null, totalCount, paginatedResults);
    }

    // Метод для получения rank леммы (здесь должна быть ваша логика)
    private double getRankForLemma(String lemma) {
        // Пример: возвращаем случайное значение rank для каждой леммы
        return Math.random() * 10; // Заменить на логику получения rank для леммы
    }

    // Метод для обработки поискового запроса
    private Set<String> processQuery(String query) {
        return Arrays.stream(query.split("\\s+"))
                .map(this::lemmatize)  // Лемматизация слова
                .filter(lemma -> !STOP_WORDS.contains(lemma))  // Фильтрация стоп-слов
                .collect(Collectors.toSet()); // Собираем уникальные леммы в Set
    }

    // Простейшая лемматизация (здесь можно заменить на реальную библиотеку или более сложную логику)
    private String lemmatize(String word) {
        word = word.toLowerCase().replaceAll("[^a-zA-Zа-яА-Я]", ""); // Убираем лишние символы
        if (word.endsWith("ы") || word.endsWith("и")) {
            return word.substring(0, word.length() - 1); // Простая замена для русского языка
        }
        return word;
    }

    // Проверка, содержит ли текст хотя бы одно слово из лемм
    private boolean containsAnyLemma(String text, Set<String> lemmas) {
        return lemmas.stream().anyMatch(text.toLowerCase()::contains);
    }

    // Генерация сниппета с выделением совпадений
    private String generateSnippet(SearchResult result, Set<String> lemmas) {
        String text = result.getTitle() + " " + result.getSnippet(); // Составляем полный текст из заголовка и сниппета
        String snippet = highlightMatches(text, lemmas); // Выделяем совпадения с запросом

        // Ограничиваем длину сниппета (например, до 300 символов, что обычно соответствует примерно трем строкам)
        if (snippet.length() > 300) {
            snippet = snippet.substring(0, 300) + "..."; // Добавляем многоточие, если сниппет длиннее 300 символов
        }

        return snippet;
    }

    // Метод для выделения совпадений
    private String highlightMatches(String text, Set<String> lemmas) {
        for (String lemma : lemmas) {
            text = text.replaceAll("(?i)(" + lemma + ")", "<b>$1</b>"); // Выделяем совпадения
        }
        return text;
    }

    // Метод для подсчета частоты лемм в индексе
    private Map<String, Double> calculateLemmaFrequency() {
        Map<String, Integer> lemmaCount = new HashMap<>();
        int totalDocuments = indexData.size();

        // Подсчитываем количество документов, в которых встречается каждая лемма
        for (SearchResult result : indexData) {
            Set<String> lemmasInDoc = processQuery(result.getTitle());
            lemmasInDoc.addAll(processQuery(result.getSnippet()));  // Дополнительно учитываем сниппет
            for (String lemma : lemmasInDoc) {
                lemmaCount.put(lemma, lemmaCount.getOrDefault(lemma, 0) + 1);
            }
        }

        // Преобразуем в частоту появления
        Map<String, Double> lemmaFrequency = new HashMap<>();
        for (Map.Entry<String, Integer> entry : lemmaCount.entrySet()) {
            double frequency = (double) entry.getValue() / totalDocuments;
            lemmaFrequency.put(entry.getKey(), frequency);
        }

        // Сортируем леммы по возрастанию частоты
        return lemmaFrequency.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new)); // LinkedHashMap сохраняет порядок
    }

    // Вспомогательный record для хранения результатов с релевантностью и сниппетом
    public record SearchResultWithRelevance(SearchResult searchResult, double absoluteRelevance, String snippet) {
    }
}
