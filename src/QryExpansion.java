import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
       
    // This value determines the number of documents to use for query expansion
    int fbDocs;
    Map<String, ArrayList<Integer>> topRankingFiles;
    
    // This value determines the number of terms that are added to the query
    int fbTerms;
    Map<Term, ArrayList<Integer>> topRankingTerms;
    DocLengthStore s;
    
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
        if (params.containsKey("fbInitialRankingFile")) {
            fbInitialRankingFile = params.get(fbInitialRankingFile);
            if (fbInitialRankingFile != null
                    && fbInitialRankingFile.length() > 0)
                hasInitialRankingFile = true;
            // TODO: read in initial ranking file
            LoadInitialRankingFile();
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
        
        topRankingFiles = new HashMap<String, ArrayList<Integer>>();
        topRankingTerms = new HashMap<Term, ArrayList<Integer>>();
        try {
            s = new DocLengthStore(QryEval.READER);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    
    void DoQueryExpansion(String query, RetrievalModel model,
            boolean isRankedModel) throws IOException {
        // pair[0]: id; pair[1]: query
        String[] pair = query.split(":");
        
        // check if has the initial ranking file
        if (!hasInitialRankingFile) {
            // use original query to retrieve the top-ranked documents
            Qryop qTree;
            qTree = QryEval.parseQuery(pair[1], model);
            QryResult result = qTree.evaluate(model);
            QryEval.sortResult(result, isRankedModel);

            // pick top fbDocs
            ArrayList<Integer> list = new ArrayList<Integer>();
            for (int i = 0; i < fbDocs; i++)
                list.add(result.docScores.scores.get(i).getDocId());
            
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
        for (Integer docid : topRankingFiles.get(pair[0])) {
            TermVector tv = new TermVector(docid, "body");
            
            // loop over each term in this document
            for (Term term : tv.terms) {
                if (!topRankingTerms.containsKey(term)) {
                    ArrayList<Integer> list = new ArrayList<Integer>();
                    list.add(docid);
                    topRankingTerms.put(term, list);
                }
                else
                    topRankingTerms.get(term).add(docid);
            }
        }
        
        // Now we have all the terms, score them
        for (Term term : topRankingTerms.keySet()) {
            ArrayList<Integer> doclist = topRankingTerms.get(term);
            double P_mle = QryEval.READER.totalTermFreq(term)
                    / (double) QryEval.READER.getSumTotalTermFreq("body");
            
            double score = Math.log(1 / P_mle);
            
            for (Integer docid : doclist) {
                
            }
            
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
            int n = 0;
            
            while ((line = br.readLine()) != null && n < fbDocs) {
                String[] part = line.split(" ");
                if (part.length != 6) {
                    System.out.println("ReadInitialRanking: Invalid trac_eval format!");
                    continue;
                }
                
                // part[0] query id; part[2] docid; part[3] rank
                int rank = Integer.parseInt(part[3]);
                if (rank > fbDocs) // in case the input file is not sorted
                    continue;
                
                int docid = QryEval.getInternalDocid(part[2]);
                
                if (!topRankingFiles.containsKey(part[0])) {
                    ArrayList<Integer> list = new ArrayList<Integer>();
                    list.add(docid);
                    topRankingFiles.put(part[0], list);
                }                    
                else
                    topRankingFiles.get(part[0]).add(docid);

                n++;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
