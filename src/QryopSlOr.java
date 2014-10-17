
import java.io.IOException;

/**
 * This class implements the OR operator for all retrieval models.
 * 
 *  @author Jiachen Li (AndrewID: jiachenl)
 */
public class QryopSlOr extends QryopSl {
    
    /**
     *  It is convenient for the constructor to accept a variable number
     *  of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
     *  @param q A query argument (a query operator).
     */
    public QryopSlOr(Qryop... q) {
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
    
    /**
     *  Evaluates the query operator, including any child operators and
     *  returns the result.
     *  @param r A retrieval model that controls how the operator behaves.
     *  @return The result of evaluating the query.
     *  @throws IOException
     */
    public QryResult evaluate(RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelUnrankedBoolean)
            return (evaluateBoolean(r));
        
        if (r instanceof RetrievalModelRankedBoolean)
            return (evaluateRankedBoolean(r));
            
        return null;
    }
    
    /**
     *  Evaluates the query operator for boolean retrieval models,
     *  including any child operators and returns the result.
     *  @param r A retrieval model that controls how the operator behaves.
     *  @return The result of evaluating the query.
     *  @throws IOException
     */
    public static final int INITIAL_VALUE = -1;
    
    public QryResult evaluateBoolean (RetrievalModel r) throws IOException {
        
        //  Initialization
        allocDaaTPtrs (r);
        QryResult result = new QryResult ();
        
        //  OR returns a document if at least one of the query arguments 
        //  occurs in the document
        int currentID = INITIAL_VALUE;
        int minID = 0/*, docNum = 0*/;
        double docScore = 1.0;
        
        while (minID != INITIAL_VALUE) {
            // initialize the minimum docID as -1
            minID = INITIAL_VALUE;
            
            // find the scoreList with minimum current docID
            for (int j = 0; j < this.daatPtrs.size(); j++) {
                DaaTPtr ptrj = this.daatPtrs.get(j);
                
                // check if this scoreList has already empty
                if (ptrj.nextDoc >= ptrj.scoreList.scores.size())
                    continue;
                
                int ptrjID = ptrj.scoreList.getDocid(ptrj.nextDoc);
                
                // check if this list has an added docID
                if (ptrjID == currentID) {
                    if (++ptrj.nextDoc >= ptrj.scoreList.scores.size())
                        continue;
                    else
                        ptrjID = ptrj.scoreList.getDocid(ptrj.nextDoc);
                }
                    
                // find the minimum
                if (minID == INITIAL_VALUE) {
                    //docNum = j;
                    minID = ptrjID;                    
                } else {
                    if (ptrjID < minID) {
                        minID = ptrjID;
                        //docNum = j;
                    }
                }
            }
            // check if finished
            if (minID == INITIAL_VALUE)
                break;
            
            // add the doc with minimum docID to result list
            // DaaTPtr ptr = this.daatPtrs.get(docNum);
            currentID = minID; //ptr.scoreList.getDocid(ptr.nextDoc++);
            result.docScores.add(minID, docScore);   
        }

        freeDaaTPtrs();
        return result;
    }
    
    
public QryResult evaluateRankedBoolean (RetrievalModel r) throws IOException {
        
        //  Initialization
        allocDaaTPtrs (r);
        QryResult result = new QryResult ();
        
        //  OR returns a document if at least one of the query arguments 
        //  occurs in the document
        int currentID = INITIAL_VALUE;
        int minID = 0/*, docNum = 0*/;
        double maxScore = 0;
        
        while (minID != INITIAL_VALUE) {
            // initialize the minimum docID as -1
            minID = INITIAL_VALUE;
            
            // find the scoreList with minimum current docID
            for (int j = 0; j < this.daatPtrs.size(); j++) {
                DaaTPtr ptrj = this.daatPtrs.get(j);
                // check if this scoreList has already empty
                if (ptrj.nextDoc >= ptrj.scoreList.scores.size())
                    continue;
                
                int ptrjID = ptrj.scoreList.getDocid(ptrj.nextDoc);
                double ptrjScore = ptrj.scoreList.getDocidScore(ptrj.nextDoc);
                
                // check if this list has an added docID
                if (ptrjID == currentID) {
                    if (++ptrj.nextDoc >= ptrj.scoreList.scores.size())
                        continue;
                    else {
                        ptrjID = ptrj.scoreList.getDocid(ptrj.nextDoc);
                        ptrjScore = ptrj.scoreList.getDocidScore(ptrj.nextDoc);
                    }
                }
                
                // find the minimum
                if (minID == INITIAL_VALUE) {
                    minID = ptrjID;
                    //docNum = j;
                    maxScore = ptrjScore;
                } else {
                    // find the doc with smallest ID but max Score
                    if (ptrjID < minID) {
                        minID = ptrjID;
                        //docNum = j;
                        maxScore = ptrjScore;
                    }
                    if (ptrjID == minID && ptrjScore > maxScore)
                        maxScore = ptrjScore;
                }
            }
            // check if finished
            if (minID == INITIAL_VALUE)
                break;
            
            // add the doc with minimum docID to result list
            //DaaTPtr ptr = this.daatPtrs.get(docNum);
            currentID = minID; //ptr.scoreList.getDocid(ptr.nextDoc++);
            result.docScores.add(minID, maxScore);   
        }

        freeDaaTPtrs();
        return result;
    }
    
    
    /**
     *  Return a string version of this query operator.  
     *  @return The string version of this query operator.
     */
    public String toString(){
        
        String result = new String ();

        for (int i=0; i<this.args.size(); i++)
          result += this.args.get(i).toString() + " ";

        return ("#OR( " + result + ")");
    }
    
    /**
     *  Calculate the default score for the specified document if it
     *  does not match the query operator.  This score is 0 for many
     *  retrieval models, but not all retrieval models.
     *  @param r A retrieval model that controls how the operator behaves.
     *  @param docid The internal id of the document that needs a default score.
     *  @return The default score.
     */
    
    // not used in #OR operator
    public double getDefaultScore (RetrievalModel r, long docid) {
        return 0.0;
    }
    
    

}
