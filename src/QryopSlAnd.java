/**
 *  This class implements the AND operator for all retrieval models.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;

public class QryopSlAnd extends QryopSl {

    /**
     * It is convenient for the constructor to accept a variable number of
     * arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
     * 
     * @param q
     *            A query argument (a query operator).
     */
    public QryopSlAnd(Qryop... q) {
        for (int i = 0; i < q.length; i++)
            this.args.add(q[i]);
    }

    /**
     * Appends an argument to the list of query operator arguments. This
     * simplifies the design of some query parsing architectures.
     * 
     * @param {q} q The query argument (query operator) to append.
     * @return void
     * @throws IOException
     */
    public void add(Qryop a) {
        this.args.add(a);
    }

    /**
     * Evaluates the query operator, including any child operators and returns
     * the result.
     * 
     * @param r
     *            A retrieval model that controls how the operator behaves.
     * @return The result of evaluating the query.
     * @throws IOException
     */
    public QryResult evaluate(RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelIndri)
            return evaluateIndri((RetrievalModelIndri)r);

        if (r instanceof RetrievalModelUnrankedBoolean)
            return (evaluateBoolean(r));

        if (r instanceof RetrievalModelRankedBoolean)
            return (evaluateRankedBoolean(r));

        return null;
    }
    
    public static final int INITIAL_VALUE = -1;
    
    /**
     * Evaluates the #and operator for Indri retrieval models, including any
     * child operators and returns the result.
     * 
     * @param r
     *            A retrieval model that controls how the operator behaves.
     * @return The result of evaluating the query.
     * @throws IOException
     */
    public QryResult evaluateIndri(RetrievalModelIndri r) throws IOException {
        // initialization
        allocDaaTPtrs (r);
        QryResult result = new QryResult ();
        
        // #and operator for the Indri 
        int minID = 0;
        double docScore = 0.0;
        double q = 1.0 / (double)this.args.size();
        
        while (true) {
            // initialize the minimum docID as -1
            minID = INITIAL_VALUE;
            docScore = 1.0;
            
            // find the scoreList with minimum current docID
            for (int j = 0; j < this.daatPtrs.size(); j++) {
                DaaTPtr ptrj = this.daatPtrs.get(j);
                
                // check if this scoreList has already empty
                if (ptrj.nextDoc >= ptrj.scoreList.scores.size())
                    continue;
                
                int ptrjID = ptrj.scoreList.getDocid(ptrj.nextDoc);
                
                if (minID == INITIAL_VALUE)
                    minID = ptrjID;                    
                else {
                    if (ptrjID < minID)
                        minID = ptrjID;
                }
            }
            
            // all the scoreList has been scanned
            if (minID == INITIAL_VALUE)
                break;
            
            // accumulate the score over the documents with the same minID
            for (int j = 0; j < this.daatPtrs.size(); j++) {
                DaaTPtr ptrj = this.daatPtrs.get(j);
                double s = 0.0; // score of current docment
                
                // if the score list not empty and current doc match minID
                if (ptrj.nextDoc < ptrj.scoreList.scores.size()
                        && ptrj.scoreList.getDocid(ptrj.nextDoc) == minID) {
                    s = ptrj.scoreList.getDocidScore(ptrj.nextDoc++);
                }
                else {
                    // find the default score
                    s = ((QryopSl) this.args.get(j)).getDefaultScore(r, minID);
                }
                
                docScore *= Math.pow(s, q);
            }
            
            // add the accumulated score to the result score list
            result.docScores.add(minID, docScore);
        }
        
        freeDaaTPtrs();
        return result;
    }

    /**
     * Evaluates the query operator for boolean retrieval models, including any
     * child operators and returns the result.
     * 
     * @param r
     *            A retrieval model that controls how the operator behaves.
     * @return The result of evaluating the query.
     * @throws IOException
     */
    public QryResult evaluateBoolean(RetrievalModel r) throws IOException {

        // Initialization

        allocDaaTPtrs(r);
        QryResult result = new QryResult();

        // Sort the arguments so that the shortest lists are first. This
        // improves the efficiency of exact-match AND without changing
        // the result.

        for (int i = 0; i < (this.daatPtrs.size() - 1); i++) {
            for (int j = i + 1; j < this.daatPtrs.size(); j++) {
                if (this.daatPtrs.get(i).scoreList.scores.size() > this.daatPtrs
                        .get(j).scoreList.scores.size()) {
                    ScoreList tmpScoreList = this.daatPtrs.get(i).scoreList;
                    this.daatPtrs.get(i).scoreList = this.daatPtrs.get(j).scoreList;
                    this.daatPtrs.get(j).scoreList = tmpScoreList;
                }
            }
        }

        // Exact-match AND requires that ALL scoreLists contain a
        // document id. Use the first (shortest) list to control the
        // search for matches.

        // Named loops are a little ugly. However, they make it easy
        // to terminate an outer loop from within an inner loop.
        // Otherwise it is necessary to use flags, which is also ugly.
        if (this.daatPtrs.size() == 0)
            return result;

        DaaTPtr ptr0 = this.daatPtrs.get(0);

        EVALUATEDOCUMENTS:
        for (; ptr0.nextDoc < ptr0.scoreList.scores.size(); ptr0.nextDoc++) {

            int ptr0Docid = ptr0.scoreList.getDocid(ptr0.nextDoc);
            double docScore = 1.0;

            // Do the other query arguments have the ptr0Docid?

            for (int j = 1; j < this.daatPtrs.size(); j++) {

                DaaTPtr ptrj = this.daatPtrs.get(j);

                while (true) {
                    if (ptrj.nextDoc >= ptrj.scoreList.scores.size())
                        break EVALUATEDOCUMENTS; // No more docs can match
                    else if (ptrj.scoreList.getDocid(ptrj.nextDoc) > ptr0Docid)
                        continue EVALUATEDOCUMENTS; // The ptr0docid can't
                                                    // match.
                    else if (ptrj.scoreList.getDocid(ptrj.nextDoc) < ptr0Docid)
                        ptrj.nextDoc++; // Not yet at the right doc.
                    else
                        break; // ptrj matches ptr0Docid
                }
            }

            // The ptr0Docid matched all query arguments, so save it.

            result.docScores.add(ptr0Docid, docScore);
        }

        freeDaaTPtrs();

        return result;
    }

    public QryResult evaluateRankedBoolean(RetrievalModel r) throws IOException {

        // Initialization

        allocDaaTPtrs(r);
        QryResult result = new QryResult();

        // Sort the arguments so that the shortest lists are first. This
        // improves the efficiency of exact-match AND without changing
        // the result.

        for (int i = 0; i < (this.daatPtrs.size() - 1); i++) {
            for (int j = i + 1; j < this.daatPtrs.size(); j++) {
                if (this.daatPtrs.get(i).scoreList.scores.size() > this.daatPtrs
                        .get(j).scoreList.scores.size()) {
                    ScoreList tmpScoreList = this.daatPtrs.get(i).scoreList;
                    this.daatPtrs.get(i).scoreList = this.daatPtrs.get(j).scoreList;
                    this.daatPtrs.get(j).scoreList = tmpScoreList;
                }
            }
        }

        // Exact-match AND requires that ALL scoreLists contain a
        // document id. Use the first (shortest) list to control the
        // search for matches.

        // Named loops are a little ugly. However, they make it easy
        // to terminate an outer loop from within an inner loop.
        // Otherwise it is necessary to use flags, which is also ugly.
        if (this.daatPtrs.size() == 0)
            return result;

        DaaTPtr ptr0 = this.daatPtrs.get(0);

        EVALUATEDOCUMENTS:
        for (; ptr0.nextDoc < ptr0.scoreList.scores.size(); ptr0.nextDoc++) {

            int ptr0Docid = ptr0.scoreList.getDocid(ptr0.nextDoc);
            double minScore = ptr0.scoreList.getDocidScore(ptr0.nextDoc);

            // Do the other query arguments have the ptr0Docid?
            for (int j = 1; j < this.daatPtrs.size(); j++) {
                DaaTPtr ptrj = this.daatPtrs.get(j);

                while (true) {
                    if (ptrj.nextDoc >= ptrj.scoreList.scores.size())
                        break EVALUATEDOCUMENTS; // No more docs can match

                    if (ptrj.scoreList.getDocid(ptrj.nextDoc) > ptr0Docid)
                        continue EVALUATEDOCUMENTS; // The ptr0docid can't
                                                    // match.

                    if (ptrj.scoreList.getDocid(ptrj.nextDoc) < ptr0Docid)
                        ptrj.nextDoc++; // Not yet at the right doc.
                    else {
                        // ptrj matches ptr0Docid
                        if (ptrj.scoreList.getDocidScore(ptrj.nextDoc) < minScore)
                            minScore = ptrj.scoreList
                                    .getDocidScore(ptrj.nextDoc);
                        break;
                    }
                }
            }

            // The ptr0Docid matched all query arguments, so save it.
            result.docScores.add(ptr0Docid, minScore);
        }

        freeDaaTPtrs();
        return result;
    }

    /**
     * Calculate the default score for #and operator.
     *  
     * @param r
     *            A retrieval model that controls how the operator behaves.
     * 
     * @param docid
     *            The internal id of the document that needs a default score.
     * 
     * @return The default score.
     */
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {

        if (r instanceof RetrievalModelIndri) {
            double docScore = 1.0;
            double s = 0.0;
            double p = 1.0 / (double) this.args.size();
            
            for (int i = 0; i < this.args.size(); i++) {
                s = ((QryopSl) this.args.get(i)).getDefaultScore(r, docid);

                docScore *= Math.pow(s, p);
            }
            
            return docScore;
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

        for (int i = 0; i < this.args.size(); i++)
            result += this.args.get(i).toString() + " ";

        return ("#AND( " + result + ")");
    }

    /**
     * Not use in this operator!
     */
    @Override
    public void addWeight(Double w) {
        // Do nothing        
    }
}
