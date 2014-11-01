import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
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
    
    // This value determines the number of terms that are added to the query
    int fbTerms;
    
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
    BufferedWriter br = null;
    
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
            br = new BufferedWriter(new FileWriter(new File(
                    fbExpansionQueryFile)));
        } catch (Exception e) {
            System.err
                    .println("Error: Invalid parameter for fbExpansionQueryFile");
            e.printStackTrace();
        }
    }

}
