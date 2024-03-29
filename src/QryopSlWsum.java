import java.io.IOException;
import java.util.ArrayList;


public class QryopSlWsum extends QryopSl {

    private ArrayList<Double> weights = null;
    
    public QryopSlWsum() {
        weights = new ArrayList<Double>();
    }


    /**
     * Appends an argument to the list of query operator arguments. This
     * simplifies the design of some query parsing architectures.
     * 
     * @param q The query argument (query operator) to append.
     * @return void
     */
    @Override
    public void add(Qryop q) {
        this.args.add(q);
    }
    
    /**
     * Add weight for corresponding argument
     * 
     * @param w
     *            weight
     */
    @Override
    public void addWeight(Double w) {
        this.weights.add(w);
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
        
        return null;
    }
    
    private static final int INITIAL_VALUE = -1;

    private QryResult evaluateIndri(RetrievalModelIndri r) throws IOException {
        // parameter verification
        if (this.args.size() != weights.size())
            QryEval.fatalError("WSUM: parameters are invalid!");
        
        // initialization
        allocDaaTPtrs (r);
        QryResult result = new QryResult ();
        
        // #wsnd operator for the Indri 
        int minID = 0;
        double docScore = 0.0;
        double wsum = 0.0;
        for (double w : weights)
            wsum += w;
        
        while (true) {
            // initialize the minimum docID as -1
            minID = INITIAL_VALUE;
            docScore = 0.0;
            
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
                    // handle the case where default score is 0, this
                    // could happen when an empty score list is created
                    // may not affect WSUM, but do so...
                    if (s == 0.0)
                        continue; // discard this term
                }
                
                docScore += (weights.get(j) / wsum) * s;
            }
            
            // add the accumulated score to the result score list
            result.docScores.add(minID, docScore);
        }
        
        freeDaaTPtrs();
        return result;
    }
    
    /**
     * Calculate the default score for #wsum operator.
     *  
     * @param r
     *            A retrieval model that controls how the operator behaves.
     * 
     * @param docid
     *            The internal id of the document that needs a default score.
     * 
     * @return The default score.
     */
    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        
        if (r instanceof RetrievalModelIndri) {
            double docScore = 0.0;
            double s = 0.0;
            double wsum = 0.0;
            for (double w : weights)
                wsum += w;
            
            for (int i = 0; i < this.args.size(); i++) {
                s = ((QryopSl) this.args.get(i)).getDefaultScore(r, docid);
                
                // handle the case where default score is 0, this
                // could happen when an empty score list is created
                // may not affect WSUM, but do so...
                if (s == 0.0)
                    continue; // discard this term
                
                docScore += (weights.get(i) / wsum) * s;
            }
            
            return docScore;
        }

        return 0.0;
    }

    @Override
    public String toString() {
        StringBuilder strbuf = new StringBuilder("#WSUM( ");

        for (int i = 0; i < this.args.size(); i++) {
            strbuf.append(weights.get(i).toString());
            strbuf.append(" ");
            strbuf.append(this.args.get(i).toString());
            strbuf.append(" ");
        }
        strbuf.append(" )");

        return strbuf.toString();
    }

}
