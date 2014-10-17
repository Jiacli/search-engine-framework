import java.io.IOException;


/**
 * Class for SUM operator in retrieval model BM25.
 * 
 * @author Jiachen Li (AndrewID: jiachenl)
 */

public class QryopSlSum extends QryopSl {

    /**
     *  It is convenient for the constructor to accept a variable number
     *  of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
     *  @param q A query argument (a query operator).
     */
    public QryopSlSum(Qryop... q) {
        for (int i = 0; i < q.length; i++)
            this.args.add(q[i]);
    }

    /**
     *  Appends an argument to the list of query operator arguments.  This
     *  simplifies the design of some query parsing architectures.
     *  @param {q} q The query argument (query operator) to append.
     *  @return void
     *  @throws IOException
     */
    public void add (Qryop a) {
        this.args.add(a);
    }
    
    public static final int INITIAL_VALUE = -1;

    /**
     *  Evaluates the query operator, including any child operators and
     *  returns the result.
     *  @param r A retrieval model that controls how the operator behaves.
     *  @return The result of evaluating the query.
     *  @throws IOException
     */
    @Override
    public QryResult evaluate(RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelBM25) {
            return evaluateBM25((RetrievalModelBM25)r);
        }

        return null;
    }
    
    private QryResult evaluateBM25(RetrievalModelBM25 r) throws IOException {
        // initialization
        allocDaaTPtrs (r);
        QryResult result = new QryResult ();
        
        // user weigth
        double k_3 = r.k_3;
        double qtf = 1.0; // suppose there is no duplicate of query term
        double user_w = (k_3 + 1) * qtf / (k_3 + qtf);
        
        // #SUM operator - sum the score of each term for the same document
        int minID = 0;
        double docScore = 0.0;
        
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
            
            // sum the score over the documents with the same minID
            for (int j = 0; j < this.daatPtrs.size(); j++) {
                DaaTPtr ptrj = this.daatPtrs.get(j);
                
                // check if this scoreList has already empty
                if (ptrj.nextDoc >= ptrj.scoreList.scores.size())
                    continue;
                
                int ptrjID = ptrj.scoreList.getDocid(ptrj.nextDoc);
                
                if (ptrjID == minID) 
                    docScore += user_w * ptrj.scoreList.getDocidScore(ptrj.nextDoc++);                
            }
            
            // add the accumulated score to the result score list
            result.docScores.add(minID, docScore);
        }
        
        freeDaaTPtrs();
        return result;        
    }

    /**
     *  Return a string version of this query operator.  
     *  @return The string version of this query operator.
     */
    @Override
    public String toString(){
        String result = new String ();

        for (int i=0; i<this.args.size(); i++)
          result += this.args.get(i).toString() + " ";

        return ("#SUM( " + result + ")");
    }

    
    /**
     *  Calculate the default score for the specified document if it
     *  does not match the query operator.  This score is 0 for many
     *  retrieval models, but not all retrieval models.
     *  @param r A retrieval model that controls how the operator behaves.
     *  @param docid The internal id of the document that needs a default score.
     *  @return The default score.
     */
    
    // not used in #SUM operator
    public double getDefaultScore (RetrievalModel r, long docid) {
        return 0.0;
    }
}
