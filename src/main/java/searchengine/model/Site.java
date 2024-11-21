package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "site")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Автоматическая генерация уникального идентификатора
    private Long id;

    @Column(columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')", nullable = false) // Поле со статусом индексации
    @Enumerated(EnumType.STRING) // Хранение значения enum как строки
    private IndexStatus status;

    @Column(name = "status_time", nullable = false) // Временная метка изменения статуса
    private LocalDateTime statusTime;

    @Column(columnDefinition = "TEXT") // Поле для хранения текста последней ошибки
    private String lastError;

    @Column(columnDefinition = "VARCHAR(255)", nullable = false) // URL сайта
    private String url;

    @Column(columnDefinition = "VARCHAR(255)", nullable = false) // Название сайта
    private String name;

    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL, orphanRemoval = true) // Связь с таблицей Page
    private List<Page> pages;
}
