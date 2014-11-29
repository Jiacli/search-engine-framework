import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
                if (!Double.isNaN(feat[i])) {
                    sb.append(" ");
                    sb.append(i + 1);
                    sb.append(":");
                    sb.append(feat[i]);
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
    String pageRankFile; // file of page rank score
    HashMap<String, Double> pageRank;
    String featureDisable; // comma-separated disabled feat, e.g.=6,9,12,15
                           // (null ok)
    boolean[] featValid;
    String svmRankLearnPath; // svm_rank_learn executable
    String svmRankClassifyPath; // svm_rank_classify executable
    double svmRankParamC = 0.001; // c parameter for SVM
    String svmRankModelFile; // svm_rank_learn will write the learned model
    String testingFeatureVectorsFile; // feature vectors for testing query
    String testingDocumentScores; // document scores svm_rank_classify write for
                                  // test

    // Indri and BM25 retrieval model
    RetrievalModel Indri;
    RetrievalModel BM25;

    BufferedWriter bw, bw_tst;

    // constructor
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
                System.out
                        .println("Warnin(gLeToR): training query id not complete!");
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
        // test feature writer
        bw_tst = new BufferedWriter(new FileWriter(testingFeatureVectorsFile));

        // initial variables
        bm25Map_body = new HashMap<String, Double>();
        bm25Map_url = new HashMap<String, Double>();
        bm25Map_title = new HashMap<String, Double>();
        bm25Map_inlink = new HashMap<String, Double>();

        indriMap_body = new HashMap<String, Double>();
        indriMap_url = new HashMap<String, Double>();
        indriMap_title = new HashMap<String, Double>();
        indriMap_inlink = new HashMap<String, Double>();

        featList = new ArrayList<SVMFeat>();
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

    ArrayList<SVMFeat> featList;

    private void cleanUp() {
        bm25Map_body.clear();
        bm25Map_url.clear();
        bm25Map_title.clear();
        bm25Map_inlink.clear();
        indriMap_body.clear();
        indriMap_url.clear();
        indriMap_title.clear();
        indriMap_inlink.clear();
        featList.clear();
    }

    public void generateTrainingFeat() throws Exception {
        String qryBody, qryUrl, qryTitle, qryInlink;

        for (String query : trainQryList) {
            // clean up;
            cleanUp();
            System.out.println("Training Query->" + query);
            // use existing code to generate 8 features for each document
            String[] pair = query.split(":"); // pair[0]:id pair[1] words
            qryBody = pair[1].trim() + " ";
            qryUrl = qryBody.replaceAll(" ", ".url ");
            qryTitle = qryBody.replaceAll(" ", ".title ");
            qryInlink = qryBody.replaceAll(" ", ".inlink ");

            // BM25
            QryResult bm25_body = QryEval.parseQuery(qryBody, BM25).evaluate(
                    BM25);
            buildMap(bm25_body, bm25Map_body);
            QryResult bm25_url = QryEval.parseQuery(qryUrl, BM25)
                    .evaluate(BM25);
            buildMap(bm25_url, bm25Map_url);
            QryResult bm25_title = QryEval.parseQuery(qryTitle, BM25).evaluate(
                    BM25);
            buildMap(bm25_title, bm25Map_title);
            QryResult bm25_inlink = QryEval.parseQuery(qryInlink, BM25)
                    .evaluate(BM25);
            buildMap(bm25_inlink, bm25Map_inlink);

            // Indri
            QryResult indri_body = QryEval.parseQuery(qryBody, Indri).evaluate(
                    Indri);
            buildMap(indri_body, indriMap_body);
            QryResult indri_url = QryEval.parseQuery(qryUrl, Indri).evaluate(
                    Indri);
            buildMap(indri_url, indriMap_url);
            QryResult indri_title = QryEval.parseQuery(qryTitle, Indri)
                    .evaluate(Indri);
            buildMap(indri_title, indriMap_title);
            QryResult indri_inlink = QryEval.parseQuery(qryInlink, Indri)
                    .evaluate(Indri);
            buildMap(indri_inlink, indriMap_inlink);

            if (!qryRelsMap.containsKey(pair[0])) {
                System.err
                        .println("Error(LeToR): Missing relevance judgment docs!");
                continue;
            }
            ArrayList<String> relFileList = qryRelsMap.get(pair[0]);

            for (String item : relFileList) {
                double[] feat = new double[18];
                for (int i = 0; i < feat.length; i++) {
                    if (!featValid[i])
                        feat[i] = Double.NaN;
                }
                String[] seg = item.trim().split(" ");
                setFeatValue(feat, seg[2], qryBody.trim());

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
                            svmFeat.feat[i] = (svmFeat.feat[i] - minVal[i])
                                    / norm;
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
        bw.close();
        System.out.println("Training feature generating completed!");
    }

    private void setFeatValue(double[] f, String extid, String qry)
            throws Exception {
        int docid = QryEval.getInternalDocid(extid);
        String[] tokens = QryEval.tokenizeQuery(qry);

        Document d = QryEval.READER.document(docid);
        // f1: spam score
        if (!Double.isNaN(f[0]))
            f[0] = Integer.parseInt(d.get("score"));

        // f2: url depth
        String rawUrl = d.get("rawUrl");
        if (!Double.isNaN(f[1])) {
            int depth = 0;
            for (int i = 0; i < rawUrl.length(); i++) {
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
                String[] stems = new String[(int) terms.size() + 1];
                TermsEnum ithTerm = terms.iterator(null);
                for (int i = 1; ithTerm.next() != null; i++) {
                    stems[i] = ithTerm.term().utf8ToString();
                }

                for (String token : tokens) {
                    if (token.length() != 0) {
                        allValid++;
                        for (int i = 1; i<stems.length; i++) {
                            if (token.equals(stems[i])) {
                                match++;
                                break;
                            }
                        }
                    }
                }

                f[6] = ((double) match) / allValid;
            }
        } else {
            // System.out.println("Doc missing field: " + docid + " " + "body");
            f[4] = Double.NaN;
            f[5] = Double.NaN;
            f[6] = Double.NaN;
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
                String[] stems = new String[(int) terms.size() + 1];
                TermsEnum ithTerm = terms.iterator(null);
                for (int i = 1; ithTerm.next() != null; i++) {
                    stems[i] = ithTerm.term().utf8ToString();
                }

                for (String token : tokens) {
                    if (token.length() != 0) {
                        allValid++;
                        for (int i = 1; i<stems.length; i++) {
                            if (token.equals(stems[i])) {
                                match++;
                                break;
                            }
                        }
                    }
                }

                f[9] = ((double) match) / allValid;
            }
        } else {
            // System.out.println("Doc missing field: " + docid + " " +
            // "title");
            f[7] = Double.NaN;
            f[8] = Double.NaN;
            f[9] = Double.NaN;
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
                String[] stems = new String[(int) terms.size() + 1];
                TermsEnum ithTerm = terms.iterator(null);
                for (int i = 1; ithTerm.next() != null; i++) {
                    stems[i] = ithTerm.term().utf8ToString();
                }

                for (String token : tokens) {
                    if (token.length() != 0) {
                        allValid++;
                        for (int i = 1; i<stems.length; i++) {
                            if (token.equals(stems[i])) {
                                match++;
                                break;
                            }
                        }
                    }
                }

                f[12] = ((double) match) / allValid;
            }
        } else {
            // System.out.println("Doc missing field: " + docid + " " + "url");
            f[10] = Double.NaN;
            f[11] = Double.NaN;
            f[12] = Double.NaN;
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
                String[] stems = new String[(int) terms.size() + 1];
                TermsEnum ithTerm = terms.iterator(null);
                for (int i = 1; ithTerm.next() != null; i++) {
                    stems[i] = ithTerm.term().utf8ToString();
                }

                for (String token : tokens) {
                    if (token.length() != 0) {
                        allValid++;
                        for (int i = 1; i<stems.length; i++) {
                            if (token.equals(stems[i])) {
                                match++;
                                break;
                            }
                        }
                    }
                }

                f[15] = ((double) match) / allValid;
            }
        } else {
            // System.out.println("Doc missing field: " + docid + " " +
            // "inlink");
            f[13] = Double.NaN;
            f[14] = Double.NaN;
            f[15] = Double.NaN;
        }

        // personal feat f17, f18
        f[16] = Double.NaN;
        f[17] = Double.NaN;
    }

    private void buildMap(QryResult result, HashMap<String, Double> map)
            throws IOException {
        for (int i = 0; i < result.docScores.scores.size(); i++) {
            int docid = result.docScores.getDocid(i);
            double score = result.docScores.getDocidScore(i);
            String extid = QryEval.getExternalDocid(docid);
            map.put(extid, score);
        }
    }

    public void generateTestFeat(ArrayList<String> testQry) throws Exception {
        String qryBody, qryUrl, qryTitle, qryInlink;

        for (String query : testQry) {
            // clean up;
            cleanUp();
            System.out.println("Test Query->" + query);

            // use existing code to generate 8 features for each document
            String[] pair = query.split(":"); // pair[0]:id pair[1] words
            qryBody = pair[1].trim() + " ";
            qryUrl = qryBody.replaceAll(" ", ".url ");
            qryTitle = qryBody.replaceAll(" ", ".title ");
            qryInlink = qryBody.replaceAll(" ", ".inlink ");

            // BM25
            QryResult bm25_body = QryEval.parseQuery(qryBody, BM25).evaluate(
                    BM25);
            buildMap(bm25_body, bm25Map_body);
            QryResult bm25_url = QryEval.parseQuery(qryUrl, BM25)
                    .evaluate(BM25);
            buildMap(bm25_url, bm25Map_url);
            QryResult bm25_title = QryEval.parseQuery(qryTitle, BM25).evaluate(
                    BM25);
            buildMap(bm25_title, bm25Map_title);
            QryResult bm25_inlink = QryEval.parseQuery(qryInlink, BM25)
                    .evaluate(BM25);
            buildMap(bm25_inlink, bm25Map_inlink);

            // Indri
            QryResult indri_body = QryEval.parseQuery(qryBody, Indri).evaluate(
                    Indri);
            buildMap(indri_body, indriMap_body);
            QryResult indri_url = QryEval.parseQuery(qryUrl, Indri).evaluate(
                    Indri);
            buildMap(indri_url, indriMap_url);
            QryResult indri_title = QryEval.parseQuery(qryTitle, Indri)
                    .evaluate(Indri);
            buildMap(indri_title, indriMap_title);
            QryResult indri_inlink = QryEval.parseQuery(qryInlink, Indri)
                    .evaluate(Indri);
            buildMap(indri_inlink, indriMap_inlink);

            // generate initial BM25 ranking
            ArrayList<String> initRankList = getInitialRanking(qryBody);

            for (String item : initRankList) {
                double[] feat = new double[18];
                for (int i = 0; i < feat.length; i++) {
                    if (!featValid[i])
                        feat[i] = Double.NaN;
                }
                setFeatValue(feat, item, qryBody.trim());

                // add feat to list for future normalization
                featList.add(new SVMFeat("0", pair[0], feat, item));
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
                            svmFeat.feat[i] = (svmFeat.feat[i] - minVal[i])
                                    / norm;
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
                bw_tst.write(svmFeat.featString());
                bw_tst.flush();
            }
        }
        bw_tst.close();
    }

    public void getRerankList(BufferedWriter out) throws Exception {
        ArrayList<String> feats = new ArrayList<String>();
        ArrayList<Double> scores = new ArrayList<Double>();

        // load feat sequence
        Scanner scan = new Scanner(new File(testingFeatureVectorsFile));
        String line = null;
        while (scan.hasNext()) {
            line = scan.nextLine();
            if (line.length() != 0) {
                String[] seg = line.split(" ");
                String qid = seg[1];
                String extid = seg[seg.length - 1];

                feats.add(qid.split(":")[1] + "\t" + extid);
            }
        }
        scan.close();

        // load score sequence
        scan = new Scanner(new File(testingDocumentScores));
        while (scan.hasNext()) {
            line = scan.nextLine();
            if (line.length() != 0)
                scores.add(Double.valueOf(line));
        }
        scan.close();

        if (feats.size() != scores.size()) {
            QryEval.fatalError("Error(LeToR): feature and re-rank score sizes mismatch!");
        }

        HashMap<String, Double> map = new HashMap<String, Double>();
        valComp cmp = new valComp();
        String qid = feats.get(0).split("\t")[0];
        for (int i = 0; i < feats.size(); i++) {
            // seg[0]: qid, seg[1]: extid
            String[] seg = feats.get(i).split("\t");
            if (qid.equals(seg[0])) {
                map.put(seg[1], scores.get(i));
            } else {
                // sort
                ArrayList<Map.Entry<String, Double>> list = new ArrayList<Map.Entry<String, Double>>();
                list.addAll(map.entrySet());
                Collections.sort(list, cmp);

                // output
                for (int j = 0; j < list.size(); j++) {
                    Map.Entry<String, Double> entry = list.get(j);
                    StringBuilder sb = new StringBuilder(qid);
                    sb.append(" Q0 ");
                    sb.append(entry.getKey());
                    sb.append(" ");
                    sb.append(j + 1);
                    sb.append(" ");
                    sb.append(entry.getValue());
                    sb.append(" Run\n");
//                    System.out.println(sb.toString());
                    out.write(sb.toString());
                    out.flush();
                }

                // prepare for next qid
                map.clear();
                qid = seg[0];
                map.put(seg[1], scores.get(i));
            }
        }

        if (!map.isEmpty()) {
            // sort
            ArrayList<Map.Entry<String, Double>> list = new ArrayList<Map.Entry<String, Double>>();
            list.addAll(map.entrySet());
            Collections.sort(list, cmp);

            // output
            for (int j = 0; j < list.size(); j++) {
                Map.Entry<String, Double> entry = list.get(j);
                StringBuilder sb = new StringBuilder(qid);
                sb.append(" Q0 ");
                sb.append(entry.getKey());
                sb.append(" ");
                sb.append(j + 1);
                sb.append(" ");
                sb.append(entry.getValue());
                sb.append(" Run\n");
//                System.out.println(sb.toString());
                out.write(sb.toString());
                out.flush();
            }
        }
    }

    private class valComp implements Comparator<Map.Entry<String, Double>> {
        public int compare(Map.Entry<String, Double> e1,
                Map.Entry<String, Double> e2) {
            if (e2.getValue() > e1.getValue())
                return 1;
            else if (e2.getValue() < e1.getValue())
                return -1;
            else
                return 0;
        }
    }

    private ArrayList<String> getInitialRanking(String query) throws Exception {
        QryResult result = QryEval.parseQuery(query, BM25).evaluate(BM25);
        QryEval.sortResult(result, true);

        ArrayList<String> topfilelist = new ArrayList<String>();

        int bound = Math.min(100, result.docScores.scores.size());
        for (int i = 0; i < bound; i++) {
            String extid = result.docScores.scores.get(i).extId;
            if (extid != null)
                topfilelist.add(extid);
            else {
                int docid = result.docScores.getDocid(i);
                topfilelist.add(QryEval.getExternalDocid(docid));
            }
        }

        return topfilelist;
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

            System.err
                    .println("Error(LeToR): Cannot find the required parameters!");
            System.exit(1);
        }
    }

    private void loadParams(Map<String, String> params) {

        trainingQueryFile = params.get("letor:trainingQueryFile");
        trainingQrelsFile = params.get("letor:trainingQrelsFile");
        trainingFeatureVectorsFile = params
                .get("letor:trainingFeatureVectorsFile");
        pageRankFile = params.get("letor:pageRankFile");
        svmRankLearnPath = params.get("letor:svmRankLearnPath");
        svmRankClassifyPath = params.get("letor:svmRankClassifyPath");
        svmRankModelFile = params.get("letor:svmRankModelFile");
        testingFeatureVectorsFile = params
                .get("letor:testingFeatureVectorsFile");
        testingDocumentScores = params.get("letor:testingDocumentScores");

        // initialize features
        featValid = new boolean[18];

        for (int i = 0; i < 18; i++) {
            featValid[i] = true;
        }

        // check which features are disabled
        if (params.containsKey("letor:featureDisable")) {
            String[] ids = params.get("letor:featureDisable").split(",");
            try {
                for (String id : ids) {
                    featValid[Integer.parseInt(id) - 1] = false;
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

    public void runSVMtrain() throws Exception {
        System.out.println("Start SVM training...");
        // runs svm_rank_learn from within Java to train the model
        // execPath is the location of the svm_rank_learn utility,
        // which is specified by letor:svmRankLearnPath in the parameter file.
        // FEAT_GEN.c is the value of the letor:c parameter.
        Process cmdProc = Runtime.getRuntime().exec(
                new String[] { svmRankLearnPath, "-c",
                        String.valueOf(svmRankParamC),
                        trainingFeatureVectorsFile, svmRankModelFile });

        // The stdout/stderr consuming code MUST be included.
        // It prevents the OS from running out of output buffer space and
        // stalling.

        // consume stdout and print it out for debugging purposes
        BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(
                cmdProc.getInputStream()));
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            System.out.println(line);
        }
        // consume stderr and print it for debugging purposes
        BufferedReader stderrReader = new BufferedReader(new InputStreamReader(
                cmdProc.getErrorStream()));
        while ((line = stderrReader.readLine()) != null) {
            System.out.println(line);
        }

        // get the return value from the executable. 0 means success, non-zero
        // indicates a problem
        if (cmdProc.waitFor() != 0) {
            throw new Exception("SVM Rank crashed.");
        } else {
            System.out.println("SVM training completed!");
        }
    }

    public void runSVMclassify() throws Exception {
        System.out.println("Run SVM ranking...");
        // runs svm_rank_learn from within Java to train the model
        // execPath is the location of the svm_rank_learn utility,
        // which is specified by letor:svmRankLearnPath in the parameter file.
        // FEAT_GEN.c is the value of the letor:c parameter.
        Process cmdProc = Runtime.getRuntime().exec(
                new String[] { svmRankClassifyPath, testingFeatureVectorsFile,
                        svmRankModelFile, testingDocumentScores });

        // The stdout/stderr consuming code MUST be included.
        // It prevents the OS from running out of output buffer space and
        // stalling.

        // consume stdout and print it out for debugging purposes
        BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(
                cmdProc.getInputStream()));
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            System.out.println(line);
        }
        // consume stderr and print it for debugging purposes
        BufferedReader stderrReader = new BufferedReader(new InputStreamReader(
                cmdProc.getErrorStream()));
        while ((line = stderrReader.readLine()) != null) {
            System.out.println(line);
        }

        // get the return value from the executable. 0 means success, non-zero
        // indicates a problem
        if (cmdProc.waitFor() != 0) {
            throw new Exception("SVM Rank crashed.");
        } else {
            System.out.println("SVM scoring completed!");
        }
    }
}
