package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.util.List;

public class LemmatizationTest {
    public static void main(String[] args) {
        try {
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();

            List<String> wordBaseForms = luceneMorph.getNormalForms("леса");

            wordBaseForms.forEach(System.out::println);
        } catch (Exception e) {
            System.err.println("Ошибка при лемматизации: " + e.getMessage());
        }
    }
}
