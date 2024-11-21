package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "lemma")
@Data
public class Lemma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "site_id", nullable = false)
    private Long siteId;  // Изменено на Long

    @Column(nullable = false, length = 255)
    private String lemma;

    @Column(nullable = false)
    private Integer frequency;

    public Lemma(String lemma) {
        this.lemma = lemma;
        this.frequency = 1;  // начальная частота
    }

    // метод для увеличения частоты
    public void increaseFrequency() {
        this.frequency++;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }
}