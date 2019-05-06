import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.io.FileInputStream;
import java.io.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.*;
import java.util.Iterator;
import java.lang.*;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Comparator;

public class WordEntry implements Serializable {
    public static HashMap<String, String> occurredInSameDoc = new HashMap<String, String>();

    class WordComparator implements Comparator<String>, Serializable {
        public transient HashMap<String, Double> contextHandle;
        public transient Index indexHandle;

        public WordComparator(HashMap<String, Double> mutualOccurrences, Index index){
            this.contextHandle = mutualOccurrences;
            this.indexHandle = index;
        }

        @Override
        public int compare(String a, String b) {
            if(contextHandle == null || indexHandle == null || indexHandle.mostOccuringEntry == null)
            {
                
                //System.out.println("!!!!!");
                
                return 0;
            }
            else {
                double mostUsedTermOccurrences = indexHandle.mostOccuringEntry.occurrences;             // Most occuring term in all Docs.
                double indexedDocuments = indexHandle.documents;                                        // Total number of indexed Docs.

                double occursA = WordEntry.of(a, indexHandle, false).occurrences;                       // Num times where term A is used.
                double occursB = WordEntry.of(b, indexHandle, false).occurrences;                       // Num times where term B is used.

                double docOccurrencesA = WordEntry.of(a, indexHandle, false).documents;                 // Num docs where word A is used.
                double docOccurrencesB = WordEntry.of(b, indexHandle, false).documents;                 // Num docs where word B is used.

                // tf = 0.5 + 0.5 (Occurrences of "A" / Most occurrences of a term " X " in all docs)
                double tfA = 0.5 + 0.5 * (occursA / mostUsedTermOccurrences);
                double tfB = 0.5 + 0.5 * (occursB / mostUsedTermOccurrences);

                // idf = Number of Documents indexed / Number of docs where the term "A" is used
                double idfA = indexedDocuments / docOccurrencesA;
                double idfB = indexedDocuments / docOccurrencesB;

                double commonOccurrencesA = WordEntry.this.mutualOccurrences.get(a);
                double commonOccurrencesB = WordEntry.this.mutualOccurrences.get(b);

                double tfidfA = commonOccurrencesA * tfA * idfA;
                double tfidfB = commonOccurrencesB * tfB * idfB;

                //System.out.println(a + "=" + tfidfA + " : " + b + "=" + tfidfB);
                //return (int)(
                //    100 * (tfidfB - tfidfA)
                //);

                if(tfidfA < tfidfB)
                    return 1;
                else if(tfidfA > tfidfB)
                    return -1;
                else
                    return 0;

            }
        }
    }

    public static final int MAX_SEARCH_RADIUS = 3;
    public static final float AGNOSTIC_WEIGHT = 0.5f;

    public String word;
    private HashMap<String, Double> mutualOccurrences;
    private HashMap<String, Double> mutualDocuments;
    public PriorityQueue<String> concepts;
    private WordComparator wordCmp;
    public int occurrences;
    public int documents;

    public WordEntry(String word, Index index){
        this.word = word;
        mutualOccurrences = new HashMap<String, Double>();
        mutualDocuments = new HashMap<String, Double>();
        wordCmp = this.new WordComparator(mutualOccurrences, index);
        concepts = new PriorityQueue<String>(10, wordCmp);
        occurrences = 0;
        documents = 0;
    }

    public void record(Index index) {
        occurrences++;

        if(index.mostOccuringEntry == null || occurrences > index.mostOccuringEntry.occurrences)
            index.mostOccuringEntry = this;
    }

    public static File resource(String word, Path index) {
        return index.resolve(Paths.get(word.hashCode() + ".dat")).toFile();
    }

    public void close(Path index) {
        try {
            FileOutputStream fileOut = new FileOutputStream(resource(this.word, index));
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(this);
            out.close();
            fileOut.close();
        }
        catch(Exception e) {
            System.out.println("[ERROR] Could not save word entry: " + e.getMessage());
        }
    }

    public void delete(Index index) {
        for(WordEntry entry : index.entries.values()){
            if(entry != this) {
                entry.mutualOccurrences.remove(this.word);
                entry.concepts.remove(this.word);
            }
        }

        try {
            resource(this.word, index.resource.toPath()).deleteOnExit();
        }
        catch(Exception e) {
            System.out.println("[ERROR] Could not delete word entry: " + e.getMessage());
        }

        index.entries.remove(word);
    }


    public static WordEntry of(String word, Index index, boolean indexing) {
        word = word.replaceAll("[^a-zA-Z]", "").toLowerCase();
        
        if(word.isEmpty())
            return null;

        WordEntry entry;
        Integer hash = word.hashCode();

        if(index.entries.containsKey(hash)){
            // Using cached version of word entry
            entry = index.entries.get(hash);
        }
        else {
            // Loading word entry from file or creating a new
            try {
                entry = WordEntry.fromFile(resource(word, index.resource.toPath()), index);
            }
            catch(IOException e) {
                entry = new WordEntry(word, index);  
                index.entries.put(hash, entry);
            }
            catch(ClassNotFoundException e) {
                entry = null;
                System.out.println("[ERROR] Should not happen!");
            }
            catch(Exception e) {
                System.out.println(e.toString());
                e.printStackTrace();
                entry = new WordEntry(word, index);  
            }

            if(indexing)
                entry.documents++;
        }

        return entry;
    }

    public static WordEntry fromFile(File file, Index index) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        ObjectInputStream ois = new ObjectInputStream(fis);

        WordEntry entry = (WordEntry) ois.readObject();

        entry.wordCmp.contextHandle = entry.mutualOccurrences;
        entry.wordCmp.indexHandle = index;
        
        index.entries.put(entry.word.hashCode(), entry);
        ois.close();

        return entry;
    }

    public static void join(WordEntry entry1, WordEntry entry2) {
        if(entry1.word.equals(entry2.word))
            return;

        assert(entry1 != null && entry2 != null);

        entry1.mutualOccurrences.put(entry2.word, entry1.mutualOccurrences.getOrDefault(entry2.word, 0.0) + 1.0);
        if(!entry1.concepts.contains(entry2.word))
            entry1.concepts.add(entry2.word);

        entry2.mutualOccurrences.put(entry1.word, entry2.mutualOccurrences.getOrDefault(entry1.word, 0.0) + 1.0);
        if(!entry2.concepts.contains(entry1.word))
            entry2.concepts.add(entry1.word);

        if(!Stream.of(entry1.word, entry2.word).anyMatch(word -> WordEntry.occurredInSameDoc.containsKey(word))){
            WordEntry.occurredInSameDoc.put(entry1.word, entry2.word);
            entry1.mutualDocuments.put(entry2.word, entry1.mutualDocuments.getOrDefault(entry2.word, 0.0) + 1.0);
            entry2.mutualDocuments.put(entry1.word, entry2.mutualDocuments.getOrDefault(entry1.word, 0.0) + 1.0);
        }
    }


    public static double closeness(WordEntry entry1, WordEntry entry2) {
        double tf =  entry1.mutualOccurrences.getOrDefault(entry2.word, 0.0) / (entry1.occurrences + entry2.occurrences);
        double idf = entry1.mutualDocuments.getOrDefault(entry2.word, 0.0) / (entry1.documents + entry2.documents);

        return tf; //*idf;
    }
}

