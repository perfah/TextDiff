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
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.*;
import java.util.Iterator;

class SSR extends TestMethod {
    Index index;

    public SSR(Path indexLocation) {
        if(indexLocation.toFile().isDirectory()){
            index = new Index(indexLocation);
        }
        else {
            index = null;
            System.out.println("[ERROR] No index found at: " + indexLocation);
        }

    }

    @Override
    public double rate(List<String> words1, List<String> words2){
        float sum = 0f;
        float maxSum = 0f; 

        for(String w1 : words1) {

            //System.out.print("word = '" + w1 + "' -> ");
            //System.out.println(WordEntry.of(w1, index).priority.stream().limit(10).collect(Collectors.toSet()));

            for(String w2 : words2) {
                if(w1.equals(w2))
                    continue;

                float frequency = 1f / (1f + (WordEntry.of(w1, index).occurrences + WordEntry.of(w2, index).occurrences));
                sum += index.getWeight(w1, w2) * frequency;
                maxSum += 1.0 * frequency;
            }
        }
            
        return sum / maxSum;
    }

    @Override
    public String toString(){
        return "SSR";
    }

}