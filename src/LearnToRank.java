import java.util.Map;

/**
 * Learning to rank model using SVM
 * Search Engines - HW5
 */

/**
 * @author JK
 *
 */
public class LearnToRank {
    
    // parameters
    String trainingQueryFile; // training queries
    String trainingQrelsFile; // relevance judgments
    String trainingFeatureVectorsFile; // write for features
    String pageRankFile;   // file of page rank score 
    String featureDisable; // comma-separated disabled feat, e.g.=6,9,12,15 (null ok)
    String svmRankLearnPath;    // svm_rank_learn executable
    String svmRankClassifyPath; // svm_rank_classify executable
    double svmRankParamC = 0.001;  // c parameter for SVM
    String svmRankModelFile;  // svm_rank_learn will write the learned model
    String testingFeatureVectorsFile; // feature vectors for testing query
    String testingDocumentScores; // document scores svm_rank_classify write for test
    
    boolean[] featValid;
    double[] feat;

    public LearnToRank(Map<String, String> params) {
        // check the existence of the parameters
        checkParams(params);
        
        // load all the parameters
        loadParams(params);
        
    }
    
    
    private void checkParams(Map<String, String> params) {
        if (!params.containsKey("letor:trainingQueryFiles")
                || !params.containsKey("letor:trainingQrelsFile")
                || !params.containsKey("letor:trainingFeatureVectorsFile")
                || !params.containsKey("letor:pageRankFile")
                || !params.containsKey("letor:svmRankLearnPath")
                || !params.containsKey("letor:svmRankClassifyPath")
                || !params.containsKey("letor:svmRankModelFile")
                || !params.containsKey("letor:testingFeatureVectorsFile")
                || !params.containsKey("letor:testingDocumentScores")) {
            
            System.err.println("Error: Cannot find the required parameters!");
            System.exit(1);
        }
    }
    
    private void loadParams(Map<String, String> params){
        
        trainingQueryFile = params.get("letor:trainingQueryFile");
        trainingQrelsFile = params.get("letor:trainingQrelsFile");
        trainingFeatureVectorsFile = params.get("letor:trainingFeatureVectorsFile");
        pageRankFile = params.get("letor:pageRankFile");
        svmRankLearnPath = params.get("letor:svmRankLearnPath");
        svmRankClassifyPath = params.get("letor:svmRankClassifyPath");
        svmRankModelFile = params.get("letor:svmRankModelFile");
        testingFeatureVectorsFile = params.get("letor:testingFeatureVectorsFile");
        testingDocumentScores = params.get("letor:testingDocumentScores");
        
        // initialize features 
        feat = new double[18];
        featValid = new boolean[18];
        
        for (int i=0; i < 18; i++) {
            feat[i] = 0.0;
            featValid[i] = true;
        }
        
        // check which features are disabled
        if (params.containsKey("letor:featureDisable")) {
            String[] ids = params.get("letor:featureDisable").split(",");
            try {
                for (String id : ids) {
                    featValid[Integer.parseInt(id)-1] = false;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        // check whether to use the default value
        if (params.containsKey("letor:svmRankParamC")) {
            String c = params.get("letor:svmRankParamC");
            try {
                svmRankParamC = Double.parseDouble(c);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
