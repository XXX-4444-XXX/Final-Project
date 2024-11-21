package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Site;
import searchengine.model.Page;
import searchengine.model.IndexStatus;
import searchengine.repositories.SiteRepository;
import searchengine.repositories.PageRepository;
import searchengine.config.SitesList;
import java.util.Random;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import edu.stanford.nlp.pipeline.*;
import java.util.*;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import searchengine.model.Lemma;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.IndexRepository;
import searchengine.model.Index;

@Service
public class IndexingService {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final ExecutorService executorService; // Executor для управления потоками
    private final AtomicBoolean isIndexingStopped = new AtomicBoolean(false); // Флаг остановки индексации
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    @Autowired
    public IndexingService(SitesList sitesList, SiteRepository siteRepository, PageRepository pageRepository,
                           LemmaRepository lemmaRepository, IndexRepository indexRepository) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.executorService = Executors.newFixedThreadPool(10); // Создаем пул потоков с 10 потоками
    }

    // Запуск индексации всех сайтов
    @Transactional
    public void startIndexing() {
        for (searchengine.config.Site configSite : sitesList.getSites()) {
            Site site = getOrCreateSite(configSite);
            clearSiteData(site); // Очистка данных сайта
            site.setStatus(IndexStatus.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);

            System.out.println("Начата индексация сайта: " + site.getUrl());
            try {
                // Запуск индексации сайта в отдельном потоке
                executorService.submit(() -> indexOrReindexSite(site));
            } catch (Exception e) {
                handleIndexingError(site, e);
            }
        }

        // Завершаем работу с пулом потоков после индексации
        shutdown();
    }

    // Повторная индексация всех сайтов
    @Transactional
    public void reindexAll() {
        for (searchengine.config.Site configSite : sitesList.getSites()) {
            Site site = getOrCreateSite(configSite);
            clearSiteData(site);
            site.setStatus(IndexStatus.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);

            System.out.println("Повторная индексация сайта: " + site.getUrl());
            try {
                // Запуск повторной индексации сайта в отдельном потоке
                executorService.submit(() -> indexOrReindexSite(site));
            } catch (Exception e) {
                handleIndexingError(site, e);
            }
        }

        // Завершаем работу с пулом потоков после повторной индексации
        shutdown();
    }

    // Остановка индексации
    @Transactional
    public void stopIndexing() {
        isIndexingStopped.set(true);
        stopAndUpdateSites(); // Обновляем статус сайтов на FAILED
        shutdown(); // Закрываем пул потоков
    }

    // Обновление статусов сайтов на FAILED, если индексация была остановлена
    private void stopAndUpdateSites() {
        for (Site site : siteRepository.findAll()) {
            if (site.getStatus() == IndexStatus.INDEXING) {
                site.setStatus(IndexStatus.FAILED);
                site.setLastError("Индексация остановлена пользователем");
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
                System.out.println("Индексация остановлена для сайта: " + site.getUrl());
            }
        }
    }

    // Очистка данных сайта перед индексацией
    @Transactional
    private void clearSiteData(Site site) {
        pageRepository.deleteBySite_Url(site.getUrl());
        System.out.println("Очистка данных сайта: " + site.getUrl());
    }

    // Получение или создание сайта
    private Site getOrCreateSite(searchengine.config.Site configSite) {
        Site site = siteRepository.findByUrl(configSite.getUrl());
        if (site == null) {
            // Создаем новый сайт со статусом INDEXING
            site = Site.builder()
                    .url(configSite.getUrl())
                    .name(configSite.getName())
                    .status(IndexStatus.INDEXING) // Используем INDEXING при создании
                    .statusTime(LocalDateTime.now())
                    .lastError(null)
                    .build();
        } else {
            // Обновляем существующий сайт перед началом индексации
            site.setName(configSite.getName());
            site.setStatus(IndexStatus.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            site.setLastError(null);
        }
        return site;
    }

    // Обработка ошибок индексации
    private void handleIndexingError(Site site, Exception e) {
        site.setStatus(IndexStatus.FAILED);
        site.setLastError("Ошибка индексации: " + e.getMessage());
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
        System.out.println("Ошибка при индексировании сайта: " + site.getUrl() + " - " + e.getMessage());
    }

    // Индексация или переиндексация сайта
    private void indexOrReindexSite(Site site) {
        Set<String> visitedUrls = new HashSet<>();
        Queue<String> urlQueue = new LinkedList<>();
        urlQueue.add(site.getUrl());

        try {
            while (!urlQueue.isEmpty()) {
                if (isIndexingStopped.get()) {
                    System.out.println("Индексация остановлена для сайта: " + site.getUrl());
                    return;
                }

                String currentUrl = urlQueue.poll();
                // Обработка страницы в текущем потоке
                crawlPage(currentUrl, site, visitedUrls, urlQueue);

                // Обновляем время статуса после обработки каждой страницы
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);  // Сохраняем обновленный статус в базе

                // Логируем обновление времени
                System.out.println("Обновлено время статуса для сайта: " + site.getUrl() + " в " + site.getStatusTime());
            }

            // После завершения обхода всех страниц меняем статус на INDEXED
            site.setStatus(IndexStatus.INDEXED);
            site.setStatusTime(LocalDateTime.now());  // Обновляем время статуса на текущий момент
            siteRepository.save(site);
            System.out.println("Индексация завершена для сайта: " + site.getUrl());

        } catch (Exception e) {
            // В случае ошибки меняем статус на FAILED и сохраняем информацию об ошибке
            handleIndexingError(site, e);
        }
    }

    // Обход страницы
    private void crawlPage(String pageUrl, Site site, Set<String> visitedUrls, Queue<String> urlQueue) {
        if (visitedUrls.contains(pageUrl)) {
            return; // Пропуск посещённой страницы
        }

        visitedUrls.add(pageUrl);

        try {
            // Задержка для имитации поведения пользователя
            Random random = new Random();
            int delay = 500 + random.nextInt(59500); // Задержка от 500 до 60000 миллисекунд (1 минуты)
            Thread.sleep(delay);


            // Подключение к странице
            Document doc = Jsoup.connect(pageUrl)
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("http://www.google.com")
                    .timeout(5000)
                    .get();

            int statusCode = doc.connection().response().statusCode();

            if (statusCode >= 300 && statusCode < 400) {
                System.out.println("Страница с редиректом пропущена: " + pageUrl + " (Код: " + statusCode + ")");
                return;
            }

            if (statusCode >= 400) {
                System.out.println("Страница пропущена из-за ошибки: " + pageUrl + " (Код: " + statusCode + ")");
                return;
            }

            String textContent = doc.text();
            List<String> lemmas = getLemmas(textContent);
            Map<String, Integer> lemmaCountMap = countLemmas(lemmas);

            Page page = Page.builder()
                    .site(site)
                    .path(pageUrl.replace(site.getUrl(), ""))
                    .code(statusCode)
                    .content(doc.html())
                    .build();
            pageRepository.save(page);
            System.out.println("Индексирована страница: " + pageUrl);

            saveLemmasAndIndex(page, lemmaCountMap);

            for (Element link : doc.select("a[href]")) {
                String nextUrl = link.absUrl("href");
                if (nextUrl.startsWith(site.getUrl()) && !visitedUrls.contains(nextUrl)) {
                    urlQueue.add(nextUrl);
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка при извлечении страницы: " + pageUrl + " - " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("Операция была прервана: " + pageUrl + " - " + e.getMessage());
            Thread.currentThread().interrupt(); // Восстанавливаем статус прерывания
        }
    }

    // Лемматизация текста
    public List<String> getLemmas(String text) {
        List<String> lemmas = new ArrayList<>();

        // Настройки StanfordCoreNLP
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma");  // Указываем аннотаторы
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        // Создание объекта Annotation для обработки текста
        Annotation document = new Annotation(text);
        pipeline.annotate(document);

        // Извлекаем леммы
        // Получаем список токенов и их лемм
        List<CoreLabel> tokens = document.get(edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation.class);
        for (CoreLabel token : tokens) {
            // Извлекаем лемму каждого токена
            String lemma = token.lemma();
            lemmas.add(lemma);
        }

        return lemmas;
    }

    // Подсчёт частоты лемм
    private Map<String, Integer> countLemmas(List<String> lemmas) {
        Map<String, Integer> lemmaCountMap = new HashMap<>();
        for (String lemma : lemmas) {
            lemmaCountMap.put(lemma, lemmaCountMap.getOrDefault(lemma, 0) + 1);
        }
        return lemmaCountMap;
    }

    // Сохранение лемм и их частот в базу данных
    private void saveLemmasAndIndex(Page page, Map<String, Integer> lemmaCountMap) {
        for (Map.Entry<String, Integer> entry : lemmaCountMap.entrySet()) {
            String lemmaText = entry.getKey();
            int count = entry.getValue();

            // Проверка на существование леммы в базе
            Lemma lemma = lemmaRepository.findByLemma(lemmaText);
            if (lemma == null) {
                lemma = new Lemma(lemmaText);
                lemma.setSiteId(page.getSite().getId());  // Привязываем лемму к сайту
                lemmaRepository.save(lemma);  // Сохраняем лемму в базе
            } else {
                // Если лемма уже существует, увеличиваем её частоту
                lemma.increaseFrequency();
                lemmaRepository.save(lemma);
            }

            // Создаем индекс для леммы и страницы
            Index index = new Index();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank((float) count); // Convert count to rank (float)

            // Сохраняем индекс для этой леммы
            indexRepository.save(index);
        }
    }


    // Закрытие ExecutorService при завершении работы
    public void shutdown() {
        executorService.shutdown();
    }
}
