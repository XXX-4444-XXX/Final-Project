package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "`index`") // Escaping the SQL reserved keyword "index"
@Data
public class Index {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    private Page page;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lemma_id", nullable = false)
    private Lemma lemma;

    @Column(name = "`rank`", nullable = false)
    private Float rank;

    // Constructor for easier creation
    public Index(Page page, Lemma lemma, Float rank) {
        this.page = page;
        this.lemma = lemma;
        this.rank = rank;
    }

    // Default constructor is still needed for JPA
    public Index() {}
}
