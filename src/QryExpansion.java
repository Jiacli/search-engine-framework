import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.Term;

/**
 * Indri's Pseudo-Relevance Feedback
 * 
 * Query Expansion
 * 
 * @author Jiachen Li
 * 
 */

public class QryExpansion {
    
    private class RankedFile {
        int docid;
        double score;
    }
       
    // This value determines the number of documents to use for query expansion
    int fbDocs;
    Map<String, ArrayList<RankedFile>> topRankingFiles;
    
    // This value determines the number of terms that are added to the query
    int fbTerms;
    Map<Term, ArrayList<Integer>> topRankingTerms;
    
    // This value determines the amount of smoothing used to calculate p(r|d).
    int fbMu;
    
    // This value determines the weight on the original query. The weight on the
    // expanded query is (1-fbOrigWeight).
    double fbOrigWeight;
    
    // A string that contains the name of a file (in trec_eval input format)
    // that contains an initial document ranking for the query.
    String fbInitialRankingFile;
    boolean hasInitialRankingFile;
    
    // A string that contains the name of a file where your program must write
    // its expansion query.
    String fbExpansionQueryFile;
    BufferedWriter bw = null;
    
    // Constructor with input parameters
    public QryExpansion(Map<String, String> params) {
        
        // check the existence of the parameters
        if (!params.containsKey("fbDocs") || !params.containsKey("fbTerms")
                || !params.containsKey("fbMu")
                || !params.containsKey("fbOrigWeight")
                || !params.containsKey("fbExpansionQueryFile")) {
            System.err.println("Error: Cannot find the required parameters!");
            System.exit(1);
        }
        
        // set fbDocs
        int fbDocs = Integer.parseInt(params.get("fbDocs"));
        if (fbDocs > 0)
            this.fbDocs = fbDocs;
        else {
            System.err.println("Error: Invalid parameter value for fbDocs");
            System.exit(1);
        }
        
        // set fbTerms
        int fbTerms = Integer.parseInt(params.get("fbTerms"));
        if (fbTerms > 0)
            this.fbTerms = fbTerms;
        else {
            System.err.println("Error: Invalid parameter for fbTerms");
            System.exit(1);
        }
        
        // set fbMu
        int fbMu = Integer.parseInt(params.get("fbMu"));
        if (fbMu >= 0)
            this.fbMu = fbMu;
        else {
            System.err.println("Error: Invalid parameter for fbMu");
            System.exit(1);
        }
        
        // set fbOrigWeight
        double fbOrigWeight = Double.parseDouble(params.get("fbOrigWeight"));
        if (fbOrigWeight >= 0.0 && fbOrigWeight <= 1.0)
            this.fbOrigWeight = fbOrigWeight;
        else {
            System.err.println("Error: Invalid parameter for fbOrigWeight");
            System.exit(1);
        }
        
        // read in initial ranking file
        topRankingFiles = new HashMap<String, ArrayList<RankedFile>>();
        
        if (params.containsKey("fbInitialRankingFile")) {
            fbInitialRankingFile = params.get("fbInitialRankingFile");
            if (fbInitialRankingFile != null
                    && fbInitialRankingFile.length() > 0) {
                hasInitialRankingFile = true;
                
                // read in initial ranking file
                LoadInitialRankingFile();
            }
        }
        
        // open output expansion query file
        fbExpansionQueryFile = params.get("fbExpansionQueryFile");

        try {
            bw = new BufferedWriter(new FileWriter(new File(
                    fbExpansionQueryFile)));
        } catch (Exception e) {
            System.err
                    .println("Error: Invalid parameter for fbExpansionQueryFile");
            e.printStackTrace();
        }
        
        topRankingTerms = new HashMap<Term, ArrayList<Integer>>();

    }

    
    String DoQueryExpansion(String query, RetrievalModel model,
            boolean isRankedModel) throws IOException {
        // pair[0]: id; pair[1]: query
        String[] pair = query.split(":");
        
        // check if has the initial ranking file
        if (!hasInitialRankingFile) {
            // use original query to retrieve the top-ranked documents
            Qryop qTree = QryEval.parseQuery(pair[1], model);
            QryResult result = qTree.evaluate(model);
            QryEval.sortResult(result, isRankedModel);

            // pick top fbDocs
            ArrayList<RankedFile> list = new ArrayList<RankedFile>();            
            
            for (int i = 0; i < fbDocs; i++) {
                RankedFile file = new RankedFile();
                file.docid = result.docScores.getDocid(i);
                file.score = result.docScores.getDocidScore(i);
                list.add(file);
            }
            
            topRankingFiles.put(pair[0], list);
        }

        // Now we get the top fbDocs files' docid
        // Let's extract potential expansion terms

        // How to use the term vector.
        // TermVector tv = new TermVector(1, "body");
        // System.out.println(tv.stemString(10)); // get the string for the 10th
        // stem
        // System.out.println(tv.stemDf(10)); // get its df
        // System.out.println(tv.totalStemFreq(10)); // get its ctf
        
        Map<String, Double> termScoreMap = new HashMap<String, Double>();

        // loop over each document
        for (RankedFile file : topRankingFiles.get(pair[0])) {
            TermVector tv = new TermVector(file.docid, "body");
            double score = file.score;
            double C = (double) QryEval.READER.getSumTotalTermFreq("body");
            long doclen = QryEval.dls.getDocLength("body", file.docid);
            
            // loop over each term in this document
            // i == 0 indicates a stopword, skip that
            for (int i = 1; i < tv.stems.length; i++) {
                // System.out.println("Term: " + tv.stems[i] + "\tfreq: " + tv.stemsFreq[i]);
                int tf = tv.stemsFreq[i];

                double P_mle = tv.totalStemFreq(i) / C;

                double s = (tf + fbMu * P_mle) / (doclen + fbMu) * score
                        * Math.log(1 / P_mle);
                
                if (!termScoreMap.containsKey(tv.stems[i]))
                    termScoreMap.put(tv.stems[i], s);
                else
                    termScoreMap.put(tv.stems[i], s + termScoreMap.get(tv.stems[i]));
            }
        }
        
        // sort the term score map
        List<Map.Entry<String,Double>> termlist = new ArrayList<Map.Entry<String,Double>>();
        termlist.addAll(termScoreMap.entrySet());
        ValueComparator valueCmp = new ValueComparator();
        Collections.sort(termlist, valueCmp);
        
        StringBuilder sb = new StringBuilder("#wand( ");
        for (int i = 0; i < fbTerms; i++) {
            sb.append(String.format("%.4f", termlist.get(i).getValue()));
            sb.append(" ");
            sb.append(termlist.get(i).getKey());
            sb.append(" ");
        }
        sb.append(")");
        
        // write expanded query to file
        bw.write(pair[0] + ": " + sb.toString() + "\n");
        
        String qryLearned = "#wand( " + fbOrigWeight + " #and(" + pair[1] + ") " + (1-fbOrigWeight) + " " + sb.toString() + ")";
        System.out.println(qryLearned);
       
        return qryLearned;      
    }
    
    // comparator to sort hashmap by value
    private static class ValueComparator implements Comparator<Map.Entry<String,Double>>  
    {  
        public int compare(Map.Entry<String,Double>  o1, Map.Entry<String,Double> o2)  
        {
            if (o2.getValue() > o1.getValue())
                return 1;
            else if (o2.getValue() < o1.getValue())
                return -1;
            else
                return 0;
        }  
    }
    
    
    
    // Read initial ranking file
    void LoadInitialRankingFile() {

        BufferedReader br = null;
        try {
            // open the initial ranking trec_eval format file
            br = new BufferedReader(new FileReader(new File(fbInitialRankingFile)));
            
            // read only top fbDocs files
            String line = null;
            
            while ((line = br.readLine()) != null) {
                String[] part = line.split(" ");
                if (part.length != 6) {
                    System.out.println("ReadInitialRanking: Invalid trac_eval format!");
                    continue;
                }
                
                // part[0] query id; part[2] docid; part[3] rank; part[4] score
                int rank = Integer.parseInt(part[3]);
                if (rank > fbDocs) // in case the input file is not sorted
                    continue;
                
                int docid = QryEval.getInternalDocid(part[2]);
                double score = Double.parseDouble(part[4]);
                
                if (!topRankingFiles.containsKey(part[0])) {
                    ArrayList<RankedFile> list = new ArrayList<RankedFile>();
                    RankedFile file = new RankedFile();
                    file.docid = docid;
                    file.score = score;
                    list.add(file);
                    topRankingFiles.put(part[0], list);
                }                    
                else {
                    RankedFile file = new RankedFile();
                    file.docid = docid;
                    file.score = score;
                    
                    topRankingFiles.get(part[0]).add(file);
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
