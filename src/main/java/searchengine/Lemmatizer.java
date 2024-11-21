package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class Lemmatizer {

    private static final Logger logger = Logger.getLogger(Lemmatizer.class.getName());

    private static final Set<String> EXCLUDED_PARTS_OF_SPEECH = Set.of("СОЮЗ", "ПРЕДЛ", "ЧАСТ", "МЕЖД");

    private static final Pattern CLEAN_PATTERN = Pattern.compile("[^а-яА-ЯёЁ\\s]");

    private final LuceneMorphology luceneMorph;

    public Lemmatizer() throws IOException {
        this.luceneMorph = new RussianLuceneMorphology();
    }

    public Map<String, Integer> getLemmasWithFrequency(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyMap();
        }

        text = cleanText(removeHtmlTags(text));

        String[] words = text.split("\\s+");

        Map<String, Integer> lemmaFrequency = new HashMap<>();

        for (String word : words) {
            String lowerCaseWord = word.toLowerCase();
            try {
                List<String> morphInfo = luceneMorph.getMorphInfo(lowerCaseWord);

                if (isExcludedPartOfSpeech(morphInfo)) continue;

                String lemma = luceneMorph.getNormalForms(lowerCaseWord).get(0);

                lemmaFrequency.put(lemma, lemmaFrequency.getOrDefault(lemma, 0) + 1);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Пропущено слово при лемматизации: " + word, e);
            }
        }

        return lemmaFrequency;
    }

    private static boolean isExcludedPartOfSpeech(List<String> morphInfo) {
        for (String info : morphInfo) {
            String[] parts = info.split("\\|");
            if (parts.length > 1 && EXCLUDED_PARTS_OF_SPEECH.contains(parts[1].split(" ")[0])) {
                return true;
            }
        }
        return false;
    }

    private static String cleanText(String text) {
        return CLEAN_PATTERN.matcher(text).replaceAll("");
    }

    private static String removeHtmlTags(String htmlText) {
        return htmlText.replaceAll("<[^>]*>", "");
    }

    public static void main(String[] args) {
        try {
            Lemmatizer lemmatizer = new Lemmatizer();

            String htmlText = "<p>Повторное появление <b>леопарда</b> в Осетии позволяет предположить, что леопард постоянно обитает в некоторых районах Северного Кавказа.</p>";

            String cleanText = removeHtmlTags(htmlText);
            Map<String, Integer> lemmaFrequency = lemmatizer.getLemmasWithFrequency(cleanText);

            lemmaFrequency.forEach((lemma, frequency) -> System.out.println(lemma + " — " + frequency));
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Ошибка инициализации морфологического анализатора", e);
        }
    }
}
