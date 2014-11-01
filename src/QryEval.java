/**
 *  QryEval illustrates the architecture for the portion of a search
 *  engine that evaluates queries.  It is a template for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 *  
 *  Revised: Jiachen Li (AndrewID: jiachenl)
 */

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Stack;
import java.util.StringTokenizer;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

public class QryEval {

    static String usage = "Usage:  java "
            + System.getProperty("sun.java.command") + " paramFile\n\n";

    // The index file reader is accessible via a global variable. This
    // isn't great programming style, but the alternative is for every
    // query operator to store or pass this value, which creates its
    // own headaches.

    public static IndexReader READER;

    // Create and configure an English analyzer that will be used for
    // query parsing.

    public static EnglishAnalyzerConfigurable analyzer = new EnglishAnalyzerConfigurable(
            Version.LUCENE_43);
    static {
        analyzer.setLowercase(true);
        analyzer.setStopwordRemoval(true);
        analyzer.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);
    }

    // document length store
    public static DocLengthStore dls;

    /**
     * @param args
     *            The only argument is the path to the parameter file.
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        // must supply parameter file
        if (args.length < 1) {
            System.err.println(usage);
            System.exit(1);
        }

        // read in the parameter file; one parameter per line in format of
        // key=value
        Map<String, String> params = new HashMap<String, String>();
        Scanner scan = new Scanner(new File(args[0]));
        String line = null;
        while (scan.hasNext()) {
            line = scan.nextLine();
            String[] pair = line.split("=");
            params.put(pair[0].trim(), pair[1].trim());
        }
        scan.close();

        // parameters required for this example to run
        if (!params.containsKey("indexPath")) {
            System.err.println("Error: Parameter 'indexPath' were missing.");
            System.exit(1);
        }

        // open the index
        READER = DirectoryReader.open(FSDirectory.open(new File(params
                .get("indexPath"))));

        if (READER == null) {
            System.err.println(usage);
            System.exit(1);
        }

        // create document length store
        dls = new DocLengthStore(READER);

        /**
         *  Start creating retrieval model
         */

        // select the retrieval model from the parameter file
        RetrievalModel model = null;
        boolean isRankedModel = false;

        if (!params.containsKey("retrievalAlgorithm")) {
            System.err
                    .println("Error: Parameter 'retrievalAlgorithm' was missing.");
            System.exit(1);
        }

        if (params.get("retrievalAlgorithm").equals("UnrankedBoolean")) {
            model = new RetrievalModelUnrankedBoolean();
        } else if (params.get("retrievalAlgorithm").equals("RankedBoolean")) {
            model = new RetrievalModelRankedBoolean();
            isRankedModel = true;
        } else if (params.get("retrievalAlgorithm").equals("BM25")) {
            model = new RetrievalModelBM25(params);
            isRankedModel = true;
        } else if (params.get("retrievalAlgorithm").equals("Indri")) {
            model = new RetrievalModelIndri(params);
            isRankedModel = true;
        } else {
            System.err.println("Error: 'retrievalAlgorithm' parameter("
                    + params.get("retrievalAlgorithm") + ") was undefined.");
            System.exit(1);
        }

        /**
         *  Start reading query from file
         */

        // read query file path from parameter "queryFilePath"
        if (!params.containsKey("queryFilePath")) {
            System.err.println("Error: Parameter 'queryFilePath' was missing.");
            System.exit(1);
        }

        // read-in queries and store them in queryList
        ArrayList<String> queryList = new ArrayList<String>();
        scan = new Scanner(new File(params.get("queryFilePath")));
        while (scan.hasNext())
            queryList.add(scan.nextLine());
        scan.close();

        // read output path from parameter "trecEvalOutputPath"
        if (!params.containsKey("trecEvalOutputPath")) {
            System.err
                    .println("Error: Parameter 'trecEvalOutputPath' was missing.");
            System.exit(1);
        }

        // create output file
        BufferedWriter br = null;
        try {
            br = new BufferedWriter(new FileWriter(new File(
                    params.get("trecEvalOutputPath"))));
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        /**
         *  Check whether to do query expansion (feedback)
         */
        boolean fb = false;
        QryExpansion qryFb = null;
        if (params.containsKey("fb") && "true".equals(params.get("fb"))) {
            qryFb = new QryExpansion(params);
            fb = true;
        }
        
        if (fb) {
            
        }
            

        /**
         *  Start evaluating query, one query a time
         */

        long startTime = 0, endTime = 0;
        double totalTime = 0;
        for (String query : queryList) {
            System.out.println("input: " + query);
            String[] pair = query.split(":"); // separate queryID and query

            // measure the running time
            startTime = System.currentTimeMillis();

            // applying query parser
            Qryop qTree = parseQuery(pair[1], model);
            QryResult result = qTree.evaluate(model);

            // sort the result first anyway
            sortResult(result, isRankedModel);

            // calculate the running time
            endTime = System.currentTimeMillis();
            System.out.println("Running time: "
                    + ((endTime - startTime) / 1000.0) + "s");
            totalTime += (endTime - startTime) / 1000.0;

            // write result to trec_eval output
            try {
                writeResultToFile(br, pair[0], result, isRankedModel);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        br.close();

        // Total running time
        System.out.println("Total running time: " + totalTime + "s");

        // Later HW assignments will use more RAM, so you want to be aware
        // of how much memory your program uses.
        printMemoryUsage(false);

        // NOTE: FOLLOWING CODES ARE RESERVED FOR HW2 & HW3

        /*
         * The code below is an unorganized set of examples that show you
         * different ways of accessing the index. Some of these are only useful
         * in HW2 or HW3.
         */

        // DocLengthStore s = new DocLengthStore(READER);

        // Lookup the document length of the body field of doc 0.
        // System.out.println(s.getDocLength("body", 0));

        // How to use the term vector.
        // TermVector tv = new TermVector(1, "body");
        // System.out.println(tv.stemString(10)); // get the string for the 10th
        // stem
        // System.out.println(tv.stemDf(10)); // get its df
        // System.out.println(tv.totalStemFreq(10)); // get its ctf
    }

    /**
     * Write the result to trec_eval - with all type
     */
    static void writeResultToFile(BufferedWriter br, String QryID,
            QryResult result, boolean isRankedModel) throws IOException {

        if (result.docScores.scores.size() < 1
                && result.invertedList.postings.size() < 1) {
            // nothing in the result
            br.write(QryID + " Q0 dummy 1 0 None\n");
        } else {
            if (result.invertedList.postings.isEmpty()) {
                // output score list
                int bound = Math.min(100, result.docScores.scores.size());
                if (isRankedModel) {
                    for (int i = 0; i < bound; i++) {
                        br.write(QryID
                                + " Q0 "
                                + getExternalDocid(result.docScores.getDocid(i))
                                + " " + (i + 1) + " "
                                + result.docScores.getDocidScore(i) + " Run\n");
                    }
                } else {
                    for (int i = 0; i < bound; i++) {
                        br.write(QryID
                                + " Q0 "
                                + getExternalDocid(result.docScores.getDocid(i))
                                + " " + (i + 1) + " " + 1.0 + " Run\n");
                    }
                }
            } else {
                // output inverted list, maybe should avoid this situation
                int bound = Math.min(100, result.invertedList.postings.size());
                if (isRankedModel) {
                    for (int i = 0; i < bound; i++) {
                        br.write(QryID
                                + " Q0 "
                                + getExternalDocid(result.invertedList
                                        .getDocid(i)) + " " + (i + 1) + " "
                                + result.invertedList.getTf(i) + " Run\n");
                    }
                } else {
                    for (int i = 0; i < bound; i++) {
                        br.write(QryID
                                + " Q0 "
                                + getExternalDocid(result.invertedList
                                        .getDocid(i)) + " " + (i + 1) + " "
                                + 1.0 + " Run\n");
                    }
                }
            }
        }

    }

    /**
     * Result Sorting - with all type
     */
    static void sortResult(QryResult result, Boolean isRankedmodel) {
        // select which list and what comparator to sort
        if (result.invertedList.postings.isEmpty()) {
            // sort score list
            if (!isRankedmodel) {
                entryComparatorUrk comp = new entryComparatorUrk();
                Collections.sort(result.docScores.scores, comp);
            } else {
                entryComparatorRk comp = new entryComparatorRk();
                Collections.sort(result.docScores.scores, comp);
            }

        } else {
            // sort inverted list
            if (!isRankedmodel) {
                postingComparatorUrk comp = new postingComparatorUrk();
                Collections.sort(result.invertedList.postings, comp);
            } else {
                postingComparatorRk comp = new postingComparatorRk();
                Collections.sort(result.invertedList.postings, comp);
            }

        }
    }

    /**
     * Score list output sorting comparator - Ranked model - since
     * getExternalDocid() is very time consuming, so I cached the id when first
     * use.
     */
    static class entryComparatorRk implements
            Comparator<ScoreList.ScoreListEntry> {
        public int compare(ScoreList.ScoreListEntry o1,
                ScoreList.ScoreListEntry o2) {
            int cmp = ScoreList.ScoreCompare(o1, o2);
            int rtn = 0;

            try {
                if (cmp == 0) {
                    if (o1.extId != null && o2.extId != null)
                        rtn = o1.extId.compareTo(o2.extId);
                    else {
                        if (o1.extId == null)
                            o1.extId = new String(
                                    getExternalDocid(o1.getDocId()));
                        if (o2.extId == null)
                            o2.extId = new String(
                                    getExternalDocid(o2.getDocId()));

                        rtn = o1.extId.compareTo(o2.extId);
                    }
                } else
                    rtn = cmp;
            } catch (Exception e) {
                e.printStackTrace();
            }

            return rtn;
        }
    }

    /**
     * Score list output sorting comparator - Unranked model - since
     * getExternalDocid() is very time consuming, so I cached the id when first
     * use.
     */
    static class entryComparatorUrk implements
            Comparator<ScoreList.ScoreListEntry> {
        public int compare(ScoreList.ScoreListEntry o1,
                ScoreList.ScoreListEntry o2) {
            int rtn = 0;

            try {
                if (o1.extId != null && o2.extId != null)
                    rtn = o1.extId.compareTo(o2.extId);
                else {
                    if (o1.extId == null)
                        o1.extId = new String(getExternalDocid(o1.getDocId()));
                    if (o2.extId == null)
                        o2.extId = new String(getExternalDocid(o2.getDocId()));

                    rtn = o1.extId.compareTo(o2.extId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            return rtn;
        }
    }

    /**
     * Inverted list output sorting comparator - Ranked model - sort by External
     * DocID - since getExternalDocid() is very time consuming, so I cached the
     * id when first use.
     */
    static class postingComparatorRk implements Comparator<InvList.DocPosting> {
        public int compare(InvList.DocPosting o1, InvList.DocPosting o2) {
            int cmp = InvList.TermCompare(o1, o2);
            int rtn = 0;

            try {
                if (cmp == 0) {
                    if (o1.extId != null && o2.extId != null)
                        rtn = o1.extId.compareTo(o2.extId);
                    else {
                        if (o1.extId == null)
                            o1.extId = new String(
                                    getExternalDocid(o1.getDocId()));
                        if (o2.extId == null)
                            o2.extId = new String(
                                    getExternalDocid(o2.getDocId()));

                        rtn = o1.extId.compareTo(o2.extId);
                    }
                } else
                    rtn = cmp;
            } catch (Exception e) {
                e.printStackTrace();
            }

            return rtn;
        }
    }

    /**
     * Inverted list output sorting comparator - Unranked model - sort by
     * External DocID - since getExternalDocid() is very time consuming, so I
     * cached the id when first use.
     */
    static class postingComparatorUrk implements Comparator<InvList.DocPosting> {
        public int compare(InvList.DocPosting o1, InvList.DocPosting o2) {
            int rtn = 0;

            try {
                if (o1.extId != null && o2.extId != null)
                    rtn = o1.extId.compareTo(o2.extId);
                else {
                    if (o1.extId == null)
                        o1.extId = new String(getExternalDocid(o1.getDocId()));
                    if (o2.extId == null)
                        o2.extId = new String(getExternalDocid(o2.getDocId()));

                    rtn = o1.extId.compareTo(o2.extId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            return rtn;
        }
    }

    /**
     * Write an error message and exit. This can be done in other ways, but I
     * wanted something that takes just one statement so that it is easy to
     * insert checks without cluttering the code.
     * 
     * @param message
     *            The error message to write before exiting.
     * @return void
     */
    static void fatalError(String message) {
        System.err.println(message);
        System.exit(1);
    }

    /**
     * Get the external document id for a document specified by an internal
     * document id. If the internal id doesn't exists, returns null.
     * 
     * @param iid
     *            The internal document id of the document.
     * @throws IOException
     */
    static String getExternalDocid(int iid) throws IOException {
        Document d = QryEval.READER.document(iid);
        String eid = d.get("externalId");
        return eid;
    }

    /**
     * Finds the internal document id for a document specified by its external
     * id, e.g. clueweb09-enwp00-88-09710. If no such document exists, it throws
     * an exception.
     * 
     * @param externalId
     *            The external document id of a document.s
     * @return An internal doc id suitable for finding document vectors etc.
     * @throws Exception
     */
    static int getInternalDocid(String externalId) throws Exception {
        Query q = new TermQuery(new Term("externalId", externalId));

        IndexSearcher searcher = new IndexSearcher(QryEval.READER);
        TopScoreDocCollector collector = TopScoreDocCollector.create(1, false);
        searcher.search(q, collector);
        ScoreDoc[] hits = collector.topDocs().scoreDocs;

        if (hits.length < 1) {
            throw new Exception("External id not found.");
        } else {
            return hits[0].doc;
        }
    }

    static final String[] term_field = { "url", "keywords", "title", "inlink", "body" };

    /**
     * parseQuery converts a query string into a query tree.
     * 
     * @param qString
     *            A string containing a query.
     * @param qTree
     *            A query tree
     * @throws IOException
     */
    static Qryop parseQuery(String qString, RetrievalModel model)
            throws IOException {
        Qryop currentOp = null;
        Stack<Qryop> stack = new Stack<Qryop>();

        // Add a default query operator to an unstructured query. This
        // is a tiny bit easier if unnecessary whitespace is removed.

        qString = qString.trim();

        if (model instanceof RetrievalModelRankedBoolean
                || model instanceof RetrievalModelUnrankedBoolean) {
            // for the boolean retrieval, add default operator #or
            qString = "#or(" + qString + ")";
        }

        if (model instanceof RetrievalModelBM25) {
            // default operator is #sum
            qString = "#sum(" + qString + ")";
        }

        if (model instanceof RetrievalModelIndri) {
            // default operator is #sum
            qString = "#and(" + qString + ")";
        }

        // Tokenize the query.

        StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()",
                true);
        String token = null;

        // used for operators with the weight arguments
        boolean hasWeight = false;
        boolean gotWeight = false;
        double weight = 0.0;
        Stack<Double> wStack = new Stack<Double>();

        // Each pass of the loop processes one token. To improve
        // efficiency and clarity, the query operator on the top of the
        // stack is also stored in currentOp.
        while (tokens.hasMoreTokens()) {

            token = tokens.nextToken();

            if (token.matches("[ ,(\t\n\r]")) {
                // Ignore most delimiters.
            } else if (token.equalsIgnoreCase("#wand")) {
                hasWeight = true;
                currentOp = new QryopSlWand();
                stack.push(currentOp);
                if (gotWeight) {
                    wStack.push(weight);
                    gotWeight = false;
                }
            } else if (token.equalsIgnoreCase("#wsum")) {
                hasWeight = true;
                currentOp = new QryopSlWsum();
                stack.push(currentOp);
                if (gotWeight) {
                    wStack.push(weight);
                    gotWeight = false;
                }
            } else if (token.equalsIgnoreCase("#sum")) {
                hasWeight = false;
                currentOp = new QryopSlSum();
                stack.push(currentOp);
            } else if (token.equalsIgnoreCase("#and")) {
                hasWeight = false;
                currentOp = new QryopSlAnd();
                stack.push(currentOp);
            } else if (token.equalsIgnoreCase("#syn")) {
                hasWeight = false;
                currentOp = new QryopIlSyn();
                stack.push(currentOp);
            } else if (token.equalsIgnoreCase("#or")) {
                hasWeight = false;
                currentOp = new QryopSlOr();
                stack.push(currentOp);
            } else if (token.toLowerCase().startsWith("#near/")) {
                hasWeight = false;
                String[] parts = token.split("/");
                try {
                    currentOp = new QryopIlNear(Integer.parseInt(parts[1]));
                    stack.push(currentOp);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (token.toLowerCase().startsWith("#window/")) {
                hasWeight = false;
                String[] parts = token.split("/");
                try {
                    currentOp = new QryopIlWindow(Integer.parseInt(parts[1]));
                    stack.push(currentOp);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (token.startsWith(")")) {
                // Finish current query operator.
                // If the current query operator is not an argument to
                // another query operator (i.e., the stack is empty when it
                // is removed), we're done (assuming correct syntax - see
                // below). Otherwise, add the current operator as an
                // argument to the higher-level operator, and shift
                // processing back to the higher-level operator.
                if (currentOp instanceof QryopSlWsum
                        || currentOp instanceof QryopSlWand) {
                    hasWeight = false;
                }
                stack.pop();

                if (stack.empty())
                    break;

                Qryop arg = currentOp;
                currentOp = stack.peek();

                // check whether the upper operator has weights
                if (currentOp instanceof QryopSlWsum
                        || currentOp instanceof QryopSlWand) {
                    hasWeight = true;
                    if (!wStack.empty() && !gotWeight) {
                        weight = wStack.pop();
                        gotWeight = true;
                    }
                    if (gotWeight) {
                        currentOp.addWeight(weight);
                        gotWeight = false;
                    }
                }
                if (arg.args.size() != 0) // in case of empty operator
                    currentOp.add(arg);
            } else {
                // NOTE: You should do lexical processing of the token before
                // creating the query term, and you should check to see whether
                // the token specifies a particular field (e.g., apple.title).

                // check if the arguments have the weights, this is used in
                // WAND & WSUM operators
                if (hasWeight && !gotWeight) { // get the weight
                    weight = Double.parseDouble(token);
                    gotWeight = true;
                    continue;
                }

                // check if token contains a potential field
                boolean hasField = false;
                for (String str : term_field) {
                    if (token.endsWith("." + str)) {
                        hasField = true;
                        String term = token.substring(0,
                                token.length() - str.length() - 1);
                        String[] terms = tokenizeQuery(term);
                        if (terms.length != 0) {
                            currentOp.add(new QryopIlTerm(terms[0], str));
                            if (hasWeight && gotWeight) {
                                currentOp.addWeight(weight);
                                gotWeight = false;
                            }
                        } else {
                            // drop the weight for stop words in hasWeight mode
                            if (hasWeight)
                                gotWeight = false;
                        }

                        break;
                    }
                }
                if (!hasField) {
                    String[] terms = tokenizeQuery(token);
                    if (terms.length != 0) {
                        currentOp.add(new QryopIlTerm(terms[0]));
                        if (hasWeight && gotWeight) {
                            currentOp.addWeight(weight);
                            gotWeight = false;
                        }
                    } else {
                        // drop the weight for stop words in hasWeight mode
                        if (hasWeight)
                            gotWeight = false;
                    }
                }
            }
        }

        // A broken structured query can leave unprocessed tokens on the
        // stack, so check for that.

        if (tokens.hasMoreTokens()) {
            System.err.println("Error:  Query syntax is incorrect. " + qString);
            System.err.println("Error part: " + tokens.nextToken());
            return null;
        }

        return currentOp;
    }

    /**
     * Print a message indicating the amount of memory used. The caller can
     * indicate whether garbage collection should be performed, which slows the
     * program but reduces memory usage.
     * 
     * @param gc
     *            If true, run the garbage collector before reporting.
     * @return void
     */
    public static void printMemoryUsage(boolean gc) {

        Runtime runtime = Runtime.getRuntime();

        if (gc) {
            runtime.gc();
        }

        System.out
                .println("Memory used:  "
                        + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L))
                        + " MB");
    }

    /**
     * Print the query results.
     * 
     * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
     * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
     * 
     * QueryID Q0 DocID Rank Score RunID
     * 
     * @param queryName
     *            Original query.
     * @param result
     *            Result object generated by {@link Qryop#evaluate()}.
     * @throws IOException
     */
    // TODO: revise the output format
    static void printResults(String queryName, QryResult result)
            throws IOException {

        System.out.println(queryName + ":  ");
        if (result.docScores.scores.size() < 1) {
            System.out.println("\tNo results.");
        } else {
            for (int i = 0; i < result.docScores.scores.size(); i++) {
                System.out.println("\t" + i + ":  "
                        + getExternalDocid(result.docScores.getDocid(i)) + ", "
                        + result.docScores.getDocidScore(i));
            }
        }
    }

    /**
     * Given a query string, returns the terms one at a time with stopwords
     * removed and the terms stemmed using the Krovetz stemmer.
     * 
     * Use this method to process raw query terms.
     * 
     * @param query
     *            String containing query
     * @return Array of query tokens
     * @throws IOException
     */
    static String[] tokenizeQuery(String query) throws IOException {

        TokenStreamComponents comp = analyzer.createComponents("dummy",
                new StringReader(query));
        TokenStream tokenStream = comp.getTokenStream();

        CharTermAttribute charTermAttribute = tokenStream
                .addAttribute(CharTermAttribute.class);
        tokenStream.reset();

        List<String> tokens = new ArrayList<String>();
        while (tokenStream.incrementToken()) {
            String term = charTermAttribute.toString();
            tokens.add(term);
        }
        return tokens.toArray(new String[tokens.size()]);
    }
}
