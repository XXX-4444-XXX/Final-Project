package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;

@Repository
public interface PageRepository extends JpaRepository<Page, Long> {

    void deleteBySite_Url(String siteUrl); // Удаление страниц по URL сайта

    // Метод для проверки, существует ли страница для конкретного сайта и пути
    boolean existsBySiteAndPath(Site site, String path); // Проверка существования страницы по сайту и пути
}
