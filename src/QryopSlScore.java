/**
 *  This class implements the SCORE operator for all retrieval models.
 *  The single argument to a score operator is a query operator that
 *  produces an inverted list.  The SCORE operator uses this
 *  information to produce a score list that contains document ids and
 *  scores.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

public class QryopSlScore extends QryopSl {

    /**
     * Construct a new SCORE operator. The SCORE operator accepts just one
     * argument.
     * 
     * @param q
     *            The query operator argument.
     * @return @link{QryopSlScore}
     */
    public QryopSlScore(Qryop q) {
        this.args.add(q);
    }

    /**
     * Construct a new SCORE operator. Allow a SCORE operator to be created with
     * no arguments. This simplifies the design of some query parsing
     * architectures.
     * 
     * @return @link{QryopSlScore}
     */
    public QryopSlScore() {
    }

    /**
     * Appends an argument to the list of query operator arguments. This
     * simplifies the design of some query parsing architectures.
     * 
     * @param q
     *            The query argument to append.
     */
    public void add(Qryop a) {
        this.args.add(a);
    }

    /**
     * Evaluate the query operator.
     * 
     * @param r
     *            A retrieval model that controls how the operator behaves.
     * @return The result of evaluating the query.
     * @throws IOException
     */
    public QryResult evaluate(RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelBM25)
            return evaluateBM25((RetrievalModelBM25) r);

        if (r instanceof RetrievalModelIndri)
            return evaulateIndri((RetrievalModelIndri) r);

        if (r instanceof RetrievalModelUnrankedBoolean)
            return (evaluateBoolean(r));

        if (r instanceof RetrievalModelRankedBoolean)
            return (evaluateRankedBoolean(r));

        return null;
    }

    
    /**
     * Evaluate the #score operator for Indri retrieval models.
     * 
     * @param r
     *            A Indri retrieval model that controls how the operator behaves.
     * @return The result of evaluating the query.
     * @throws IOException
     */
    public QryResult evaulateIndri(RetrievalModelIndri r) throws IOException {
        
        // initialization
        QryResult result = args.get(0).evaluate(r);
        double mu = r.mu;
        double lambda = r.lambda;
        
        this.field = result.invertedList.field; // field of this term
        int ctf = result.invertedList.ctf; // collection term frequency
        // the length of a field
        //long lengthC = QryEval.READER.getSumTotalTermFreq(field);

        this.P_mle = ctf / (double) QryEval.READER.getSumTotalTermFreq(this.field);
        
        // grade each document
        for (int i = 0; i < result.invertedList.df; i++) {
            // get docid, tf and doclen
            int docid = result.invertedList.postings.get(i).docid;
            int tf = result.invertedList.postings.get(i).tf;
            long doclen = QryEval.dls.getDocLength(field, docid);
            
            double p = lambda * (tf + mu * this.P_mle) / (doclen + mu)
                    + (1 - lambda) * this.P_mle;
            
            // add to result score list
            result.docScores.add(docid, p);
        }
        
        // The SCORE operator should not return a populated inverted list.
        // If there is one, replace it with an empty inverted list.
        if (result.invertedList.df > 0)
            result.invertedList = new InvList();

        return result;
    }

    /**
     * Evaluate the #score operator for BM25 retrieval models.
     * 
     * @param r
     *            A BM25 retrieval model that controls how the operator behaves.
     * @return The result of evaluating the query.
     * @throws IOException
     */
    public QryResult evaluateBM25(RetrievalModelBM25 r) throws IOException {

        // initialization
        QryResult result = args.get(0).evaluate(r);

        int N = QryEval.READER.numDocs(); // the total document number
        int df = result.invertedList.df; // document frequency of this term
        double k_1 = r.k_1;
        double b = r.b;
        String field = result.invertedList.field; // field of this term

        // grade each document
        double idf_w = Math.log((N - df + 0.5) / (df + 0.5));
        double avg_doclen = QryEval.READER.getSumTotalTermFreq(field)
                / (double) QryEval.READER.getDocCount(field);

        for (int i = 0; i < df; i++) {
            // get docid and tf
            int docid = result.invertedList.postings.get(i).docid;
            int tf = result.invertedList.postings.get(i).tf;

            // calculate weights and score
            long doclen = QryEval.dls.getDocLength(field, docid);
            double tf_w = tf
                    / (tf + k_1 * ((1 - b) + b * (doclen / avg_doclen)));
            double score = idf_w * tf_w;

            result.docScores.add(docid, score);
        }

        // The SCORE operator should not return a populated inverted list.
        // If there is one, replace it with an empty inverted list.
        if (result.invertedList.df > 0)
            result.invertedList = new InvList();

        return result;
    }

    /**
     * Evaluate the #score operator for unranked boolean retrieval models.
     * 
     * @param r
     *            A retrieval model that controls how the operator behaves.
     * @return The result of evaluating the query.
     * @throws IOException
     */
    public QryResult evaluateBoolean(RetrievalModel r) throws IOException {

        // Evaluate the query argument.

        QryResult result = args.get(0).evaluate(r);

        // Each pass of the loop computes a score for one document. Note:
        // If the evaluate operation above returned a score list (which is
        // very possible), this loop gets skipped.

        for (int i = 0; i < result.invertedList.df; i++) {
            // DIFFERENT RETRIEVAL MODELS IMPLEMENT THIS DIFFERENTLY.
            // Unranked Boolean. All matching documents get a score of 1.0.
            result.docScores.add(result.invertedList.postings.get(i).docid,
                    (float) 1.0);
        }

        // The SCORE operator should not return a populated inverted list.
        // If there is one, replace it with an empty inverted list.
        if (result.invertedList.df > 0)
            result.invertedList = new InvList();

        return result;
    }

    /**
     * Evaluate the #score operator for ranked boolean retrieval models.
     * 
     * @param r
     *            A retrieval model that controls how the operator behaves.
     * @return The result of evaluating the query.
     * @throws IOException
     */
    public QryResult evaluateRankedBoolean(RetrievalModel r) throws IOException {

        // Evaluate the query argument.

        QryResult result = args.get(0).evaluate(r);

        // Each pass of the loop computes a score for one document. Note:
        // If the evaluate operation above returned a score list (which is
        // very possible), this loop gets skipped.

        for (int i = 0; i < result.invertedList.df; i++) {
            // Ranked Boolean, use tf as the score.
            result.docScores.add(result.invertedList.postings.get(i).docid,
                    (double) result.invertedList.postings.get(i).tf);
        }

        // The SCORE operator should not return a populated inverted list.
        // If there is one, replace it with an empty inverted list.
        if (result.invertedList.df > 0)
            result.invertedList = new InvList();

        return result;
    }
    
    // cache for calculating default score
    private double P_mle;  // used in Indri model
    private String field;  // field of inverted list
    

    /**
     * Calculate the default score for #score operator.
     * 
     * @param r A retrieval model that controls how the operator behaves.
     * 
     * @param docid The internal id of the document that needs a default score.
     * 
     * @return The default score.
     */
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        if (r instanceof RetrievalModelIndri) {
            double mu = ((RetrievalModelIndri) r).mu;
            double lambda = ((RetrievalModelIndri) r).lambda;
            long doclen = QryEval.dls.getDocLength(field, (int)docid);
            
            double p = lambda * mu * P_mle / (doclen + mu)
                    + (1 - lambda) * P_mle;
            
            return p;
        }

        return 0.0;
    }

    /**
     * Return a string version of this query operator.
     * 
     * @return The string version of this query operator.
     */
    public String toString() {

        String result = new String();

        for (Iterator<Qryop> i = this.args.iterator(); i.hasNext();)
            result += (i.next().toString() + " ");

        return ("#SCORE( " + result + ")");
    }

    /**
     * Not use in this operator!
     */
    @Override
    public void addWeight(Double w) {
        // Do nothing        
    }
}
