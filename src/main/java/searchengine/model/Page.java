package searchengine.model;

import jakarta.persistence.*;  // Ensure correct import for JPA annotations
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

@Entity
@Table(
        name = "page",
        indexes = @Index(name = "idx_path", columnList = "path") // Correct placement of @Index
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    // Field for path with a maximum length of 512
    @Column(name = "path", length = 512, nullable = false)
    private String path;

    // HTTP response code for the page
    @Column(name = "code", nullable = false)
    private int code;

    // Page content stored as MEDIUMTEXT
    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
}