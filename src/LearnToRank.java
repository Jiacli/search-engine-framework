import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;


/**
 * Learning to rank model using SVM
 * Search Engines - HW5
 */

/**
 * @author JK
 *
 */
public class LearnToRank {
    // class SVMFeat
    private class SVMFeat {
        String rel;
        String qid;
        double[] feat = new double[18];
        String extid;
        
        public SVMFeat(String rel, String qid, double[] feat, String extid) {
            this.rel = new String(rel);
            this.qid = new String(qid);
            this.extid = new String(extid);
            for (int i = 0; i < this.feat.length; i++) {
                this.feat[i] = feat[i];
            }
        }
        
        public String featString() {
            StringBuilder sb = new StringBuilder(rel);
            sb.append(" qid:");
            sb.append(qid);
            for (int i = 0; i < feat.length; i++) {
                sb.append(" ");
                sb.append(i+1);
                sb.append(":");
                if (!Double.isNaN(feat[i])) {
                    sb.append(feat[i]);
                } else {
                    sb.append("0");
                }
            }
            sb.append(" # ");
            sb.append(extid);
            sb.append("\n");
            return sb.toString();
        }
    }
    
    // parameters
    String trainingQueryFile; // training queries
    ArrayList<String> trainQryList;
    String trainingQrelsFile; // relevance judgments
    HashMap<String, ArrayList<String>> qryRelsMap;
    String trainingFeatureVectorsFile; // write for features
    String pageRankFile;   // file of page rank score
    HashMap<String, Double> pageRank;
    String featureDisable; // comma-separated disabled feat, e.g.=6,9,12,15 (null ok)
    String svmRankLearnPath;    // svm_rank_learn executable
    String svmRankClassifyPath; // svm_rank_classify executable
    double svmRankParamC = 0.001;  // c parameter for SVM
    String svmRankModelFile;  // svm_rank_learn will write the learned model
    String testingFeatureVectorsFile; // feature vectors for testing query
    String testingDocumentScores; // document scores svm_rank_classify write for test
    
    boolean[] featValid;
    
    // Indri and BM25 retrieval model
    RetrievalModel Indri;
    RetrievalModel BM25;
    
    BufferedWriter bw;
    

    public LearnToRank(Map<String, String> params) throws Exception {
        // check the existence of the parameters
        checkParams(params);
        
        // load all the parameters
        loadParams(params);
        
        // initialize retrieval model
        BM25 = new RetrievalModelBM25(params);
        Indri = new RetrievalModelIndri(params);
        
        // load training query
        trainQryList = new ArrayList<String>();
        qryRelsMap = new HashMap<String, ArrayList<String>>();
        Scanner scan = new Scanner(new File(trainingQueryFile));
        while (scan.hasNext()) {
            String qry = scan.nextLine();
            trainQryList.add(qry);
            String id = qry.split(":")[0];
            qryRelsMap.put(id, new ArrayList<String>());            
        }
        scan.close();
        
        // load relevance judgments file
        scan = new Scanner(new File(trainingQrelsFile));
        while (scan.hasNext()) {
            String line = scan.nextLine();
            if (line.length() == 0)
                continue;
            
            String id = line.split(" ")[0];
            if (!qryRelsMap.containsKey(id)) {
                System.out.println("Warnin(gLeToR): training query id not complete!");
                qryRelsMap.put(id, new ArrayList<String>());
            }
            qryRelsMap.get(id).add(line);
        }
        scan.close();
        
        // load page rank
        pageRank = new HashMap<String, Double>();
        scan = new Scanner(new File(pageRankFile));
        while (scan.hasNext()) {
            String line = scan.nextLine();
            if (line.length() == 0)
                continue;
            
            String[] seg = line.split("\t");
            if (seg.length != 2)
                continue;

            pageRank.put(seg[0], Double.valueOf(seg[1]));
        }
        scan.close();
        
        // training feature writer
        bw = new BufferedWriter(new FileWriter(trainingFeatureVectorsFile));
          
    }
    
    // variables and functions for training data generating
    private HashMap<String, Double> bm25Map_body;
    private HashMap<String, Double> bm25Map_url;
    private HashMap<String, Double> bm25Map_title;
    private HashMap<String, Double> bm25Map_inlink;
    
    private HashMap<String, Double> indriMap_body;
    private HashMap<String, Double> indriMap_url;
    private HashMap<String, Double> indriMap_title;
    private HashMap<String, Double> indriMap_inlink;
    
    private void cleanUp() {
        bm25Map_body.clear();
        bm25Map_url.clear();
        bm25Map_title.clear();
        bm25Map_inlink.clear();
        indriMap_body.clear();
        indriMap_url.clear();
        indriMap_title.clear();
        indriMap_inlink.clear();
    }
    
    public void generateTrainingFeat() throws Exception {
        String qryBody, qryUrl, qryTitle, qryInlink;
        bm25Map_body = new HashMap<String, Double>();
        bm25Map_url = new HashMap<String, Double>();
        bm25Map_title = new HashMap<String, Double>();
        bm25Map_inlink = new HashMap<String, Double>();
        
        indriMap_body = new HashMap<String, Double>();
        indriMap_url = new HashMap<String, Double>();
        indriMap_title = new HashMap<String, Double>();
        indriMap_inlink = new HashMap<String, Double>();

        for (String query : trainQryList) {
            // clean up;
            cleanUp();
            
            // use existing code to generate 8 features for each document
            String[] pair = query.split(":"); // pair[0]:id pair[1] words
            qryBody = pair[1].trim()+" ";
            qryUrl = qryBody.replaceAll(" ", ".url ");
            qryTitle = qryBody.replaceAll(" ", ".title ");
            qryInlink = qryBody.replaceAll(" ", ".inlink ");

            // BM25
            QryResult bm25_body = QryEval.parseQuery(qryBody, BM25).evaluate(BM25);
            buildMap(bm25_body,bm25Map_body);
            QryResult bm25_url = QryEval.parseQuery(qryUrl, BM25).evaluate(BM25);
            buildMap(bm25_url,bm25Map_url);
            QryResult bm25_title = QryEval.parseQuery(qryTitle, BM25).evaluate(BM25);
            buildMap(bm25_title,bm25Map_title);
            QryResult bm25_inlink = QryEval.parseQuery(qryInlink, BM25).evaluate(BM25);
            buildMap(bm25_inlink,bm25Map_inlink);
            
            // Indri
            QryResult indri_body = QryEval.parseQuery(qryBody, Indri).evaluate(Indri);
            buildMap(indri_body,indriMap_body);
            QryResult indri_url = QryEval.parseQuery(qryUrl, Indri).evaluate(Indri);
            buildMap(indri_url,indriMap_url);
            QryResult indri_title = QryEval.parseQuery(qryTitle, Indri).evaluate(Indri);
            buildMap(indri_title,indriMap_title);
            QryResult indri_inlink = QryEval.parseQuery(qryInlink, Indri).evaluate(Indri);
            buildMap(indri_inlink,indriMap_inlink);
            
//            System.out.println(qryBody+"\n"+qryUrl+"\n"+qryInlink+"\n"+qryTitle);
            if (!qryRelsMap.containsKey(pair[0])) {
                System.err.println("Error(LeToR): Missing relevance judgment docs!");
                continue;
            }
            ArrayList<String> relFileList = qryRelsMap.get(pair[0]);
            ArrayList<SVMFeat> featList = new ArrayList<SVMFeat>();
            
            for (String item : relFileList) {
                double[] feat = new double[18];
                for (int i = 0; i < feat.length; i++) {
                    if (!featValid[i])
                        feat[i] = Double.NaN;
                }
                
                String[] seg = item.trim().split(" ");
                setFeatValue(feat,seg[2],qryBody.trim());
                // add feat to list for future normalization
                featList.add(new SVMFeat(seg[3], seg[0], feat, seg[2]));
            }
            
            // normalization
            double[] maxVal = new double[18];
            double[] minVal = new double[18];
            for (int i = 0; i < maxVal.length; i++) {
                maxVal[i] = Double.MIN_VALUE;
                minVal[i] = Double.MAX_VALUE;
            }
            
            // find max, min for each feature
            for (SVMFeat svmFeat : featList) {
                for (int i = 0; i < svmFeat.feat.length; i++) {
                    if (!Double.isNaN(svmFeat.feat[i])) {
                        if (svmFeat.feat[i] > maxVal[i])
                            maxVal[i] = svmFeat.feat[i];
                        if (svmFeat.feat[i] < minVal[i])
                            minVal[i] = svmFeat.feat[i];
                    }
                }
            }
            
            // do normalization
            for (int i = 0; i < maxVal.length; i++) {
                if (maxVal[i] != minVal[i]) {
                    double norm = maxVal[i] - minVal[i];
                    for (SVMFeat svmFeat : featList) {
                        if (!Double.isNaN(svmFeat.feat[i])) {
                            svmFeat.feat[i] = (svmFeat.feat[i] - minVal[i]) / norm;
                        }                            
                    }
                } else {
                    for (SVMFeat svmFeat : featList) {
                        if (!Double.isNaN(svmFeat.feat[i]))
                            svmFeat.feat[i] = 0.0;
                    }
                }
            }
            
            // write feature vector to file
            for (SVMFeat svmFeat : featList) {
                bw.write(svmFeat.featString());
                bw.flush();
            }
        }
    }
    
    private void setFeatValue(double[] f, String extid, String qry) throws Exception {
        int docid = QryEval.getInternalDocid(extid);
        
        Document d = QryEval.READER.document(docid);
        // f1: spam score
        if (!Double.isNaN(f[0]))
            f[0] = Integer.parseInt(d.get("score"));
        
        // f2: url depth
        String rawUrl = d.get("rawUrl");
        if (!Double.isNaN(f[1])) {            
            int depth = 0;
            for (int i=0; i < rawUrl.length(); i++) {
                if (rawUrl.charAt(i) == '/')
                    depth++;
            }
            f[1] = depth;
        }
        
        // f3: wikipedia score
        if (!Double.isNaN(f[2]) && rawUrl.contains("wikipedia.org"))
            f[2] = 1.0;
        
        // f4: page rank
        if (!Double.isNaN(f[3]) && pageRank.containsKey(extid)) {
            f[3] = pageRank.get(extid);
        } else {
            f[3] = Double.NaN;
        }
        
        // f5,f6,f7: score for <q, d_body>
        Terms terms = QryEval.READER.getTermVector(docid, "body");
        if (terms != null) {
            if (!Double.isNaN(f[4])) {
                if (bm25Map_body.containsKey(extid))
                    f[4] = bm25Map_body.get(extid);
                else
                    f[4] = 0.0;
            }

            if (!Double.isNaN(f[5])) {
                if (indriMap_body.containsKey(extid))
                    f[5] = indriMap_body.get(extid);
                else
                    f[5] = 0.0;
            }

            if (!Double.isNaN(f[6])) {
                int match = 0;
                int allValid = 0;
                String[] stems = new String[(int) terms.size()+1];
                TermsEnum ithTerm = terms.iterator(null);
                for (int i = 1; ithTerm.next() != null; i++) {
                    stems[i] = ithTerm.term().utf8ToString();
                }
                
                String[] qryTerms = qry.split(" ");
                for (String qryTerm : qryTerms) {
                    if (qryTerm.length() != 0) {
                        allValid++;
                        for (String stem : stems) {
                            if (qryTerm.equals(stem)) {
                                match++;
                                break;
                            }
                        }
                    }
                }
                
                f[6] = ((double) match) / allValid;
            }
        } else {
            System.out.println("Doc missing field: " + docid + " " + "body");
            f[4] = 0.0;
            f[5] = 0.0;
            f[6] = 0.0;
        }
        
        // f8,f9,f10: score for <q, d_title>
        terms = QryEval.READER.getTermVector(docid, "title");
        if (terms != null) {
            if (!Double.isNaN(f[7])) {
                if (bm25Map_title.containsKey(extid))
                    f[7] = bm25Map_title.get(extid);
                else
                    f[7] = 0.0;
            }
                
            if (!Double.isNaN(f[8])) {
                if (indriMap_title.containsKey(extid))
                    f[8] = indriMap_title.get(extid);
                else
                    f[8] = 0.0;
            }

            if (!Double.isNaN(f[9])) {
                int match = 0;
                int allValid = 0;
                String[] stems = new String[(int) terms.size()+1];
                TermsEnum ithTerm = terms.iterator(null);
                for (int i = 1; ithTerm.next() != null; i++) {
                    stems[i] = ithTerm.term().utf8ToString();
                }
                
                String[] qryTerms = qry.split(" ");
                for (String qryTerm : qryTerms) {
                    if (qryTerm.length() != 0) {
                        allValid++;
                        for (String stem : stems) {
                            if (qryTerm.equals(stem)) {
                                match++;
                                break;
                            }
                        }
                    }
                }
                
                f[9] = ((double) match) / allValid;
            }
        } else {
            System.out.println("Doc missing field: " + docid + " " + "title");
            f[7] = 0.0;
            f[8] = 0.0;
            f[9] = 0.0;
        }

        // f11,f12,f13: score for <q, d_url>
        terms = QryEval.READER.getTermVector(docid, "url");
        if (terms != null) {
            if (!Double.isNaN(f[10])) {
                if (bm25Map_url.containsKey(extid))
                    f[10] = bm25Map_url.get(extid);
                else {
                    f[10] = 0.0;
                }
            }
                
            
            if (!Double.isNaN(f[11])) {
                if (indriMap_url.containsKey(extid))
                    f[11] = indriMap_url.get(extid);
                else {
                    f[11] = 0.0;
                }
            }
                
            
            if (!Double.isNaN(f[12])) {
                int match = 0;
                int allValid = 0;
                String[] stems = new String[(int) terms.size()+1];
                TermsEnum ithTerm = terms.iterator(null);
                for (int i = 1; ithTerm.next() != null; i++) {
                    stems[i] = ithTerm.term().utf8ToString();
                }
                
                String[] qryTerms = qry.split(" ");
                for (String qryTerm : qryTerms) {
                    if (qryTerm.length() != 0) {
                        allValid++;
                        for (String stem : stems) {
                            if (qryTerm.equals(stem)) {
                                match++;
                                break;
                            }
                        }
                    }
                }
                
                f[12] = ((double) match) / allValid;
            }
        } else {
            System.out.println("Doc missing field: " + docid + " " + "url");
            f[10] = 0.0;
            f[11] = 0.0;
            f[12] = 0.0;
        }

        // f14,f15,f16: score for <q, d_inlink>
        terms = QryEval.READER.getTermVector(docid, "inlink");
        if (terms != null) {
            if (!Double.isNaN(f[13])) {
                if (bm25Map_inlink.containsKey(extid))
                    f[13] = bm25Map_inlink.get(extid);
                else {
                    f[13] = 0.0;
                }
            }
                
            if (!Double.isNaN(f[14])) {
                if (indriMap_inlink.containsKey(extid))
                    f[14] = indriMap_inlink.get(extid);
                else
                    f[14] = 0.0;                    
            }
        
            if (!Double.isNaN(f[15])) {
                int match = 0;
                int allValid = 0;
                String[] stems = new String[(int) terms.size()+1];
                TermsEnum ithTerm = terms.iterator(null);
                for (int i = 1; ithTerm.next() != null; i++) {
                    stems[i] = ithTerm.term().utf8ToString();
                }
                
                String[] qryTerms = qry.split(" ");
                for (String qryTerm : qryTerms) {
                    if (qryTerm.length() != 0) {
                        allValid++;
                        for (String stem : stems) {
                            if (qryTerm.equals(stem)) {
                                match++;
                                break;
                            }
                        }
                    }
                }
                
                f[15] = ((double) match) / allValid;
            }
        } else {
            System.out.println("Doc missing field: " + docid + " " + "inlink");
            f[13] = 0.0;
            f[14] = 0.0;
            f[15] = 0.0;
        }
        
        // personal feat f17, f18
        f[16] = Double.NaN;
        f[17] = Double.NaN;
    }
    
    private void buildMap(QryResult result, HashMap<String, Double> map) throws IOException {
        for (int i = 0; i < result.docScores.scores.size(); i++) {
            int docid = result.docScores.getDocid(i);
            double score = result.docScores.getDocidScore(i);
            String extid = QryEval.getExternalDocid(docid);
            map.put(extid, score);
        }
    }

    
    
    private void checkParams(Map<String, String> params) {
        if (!params.containsKey("letor:trainingQueryFile")
                || !params.containsKey("letor:trainingQrelsFile")
                || !params.containsKey("letor:trainingFeatureVectorsFile")
                || !params.containsKey("letor:pageRankFile")
                || !params.containsKey("letor:svmRankLearnPath")
                || !params.containsKey("letor:svmRankClassifyPath")
                || !params.containsKey("letor:svmRankModelFile")
                || !params.containsKey("letor:testingFeatureVectorsFile")
                || !params.containsKey("letor:testingDocumentScores")) {
            
            System.err.println("Error(LeToR): Cannot find the required parameters!");
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
        featValid = new boolean[18];
        
        for (int i=0; i < 18; i++) {
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
