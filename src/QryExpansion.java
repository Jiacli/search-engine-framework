import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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
    ArrayList<Integer> topRankingFiles;
    
    // This value determines the number of terms that are added to the query
    int fbTerms;
    Map<String, ArrayList<Integer>> topRankingTerms;
    
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
        
        topRankingFiles = new ArrayList<Integer>();
        topRankingTerms = new HashMap<String, ArrayList<Integer>>();
        
    }

    
    void DoQueryExpansion(String oriQuery, RetrievalModel model,
            boolean isRankedModel) throws IOException {
        // check if has the initial ranking file
        if (hasInitialRankingFile)
            LoadInitialRankingFile();
        else {
            // use original query to retrieve the top-ranked documents
            Qryop qTree;
            qTree = QryEval.parseQuery(oriQuery, model);
            QryResult result = qTree.evaluate(model);
            QryEval.sortResult(result, isRankedModel);

            // pick top fbDocs
            for (int i = 0; i < fbDocs; i++)
                topRankingFiles.add(result.docScores.scores.get(i).getDocId());
        }

        // Now we get the top fbDocs files' docid
        // Let's extract potential expansion terms

        // How to use the term vector.
        // TermVector tv = new TermVector(1, "body");
        // System.out.println(tv.stemString(10)); // get the string for the 10th
        // stem
        // System.out.println(tv.stemDf(10)); // get its df
        // System.out.println(tv.totalStemFreq(10)); // get its ctf

        for (Integer docid : topRankingFiles) {
            TermVector tv = new TermVector(docid, "body");
            
            for (String stem : tv.stems) {
                if (!topRankingTerms.containsKey(stem)) {
                    ArrayList<Integer> list = new ArrayList<Integer>();
                    list.add(docid);
                    topRankingTerms.put(stem, list);
                }
                else
                    topRankingTerms.get(stem).add(docid);
            }
        }
        
        // Now we have all the terms, score them
        for (String key : topRankingTerms.keySet()) {
            ArrayList<Integer> doclist = topRankingTerms.get(key);
            double score = 0.0;
            
            
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
                
                // part[2] docid, part[3] rank
                int rank = Integer.parseInt(part[3]);
                if (rank > fbDocs) // in case the input file is not sorted
                    continue;
                
                int docid = QryEval.getInternalDocid(part[2]);
                
                topRankingFiles.add(docid);
                n++;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
