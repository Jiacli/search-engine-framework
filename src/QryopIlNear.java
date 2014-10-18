/**
 *  This class implements the SYN operator for all retrieval models.
 *  The synonym operator creates a new inverted list that is the union
 *  of its constituents.  Typically it is used for morphological or
 *  conceptual variants, e.g., #SYN (cat cats) or #SYN (cat kitty) or
 *  #SYN (astronaut cosmonaut).
 *
 *  @author Jiachen Li
 *  AndrewID: jiachenl
 */

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;


public class QryopIlNear extends QryopIl {
    
    // the specified distance 'n' in "NEAR/n" operator
    private int dis = 0;
    
    /**
     *  It is convenient for the constructor to accept a variable number
     *  of arguments. Thus new QryopIlSyn (arg1, arg2, arg3, ...).
     */
    public QryopIlNear(int dis, Qryop... q) {
        this.dis = dis;
        
        for (int i = 0; i < q.length; i++)
            this.args.add(q[i]);
    }
    
    public QryopIlNear(int dis) {
        // distance should greater than 0
        if (dis <= 0)
            QryEval.fatalError("'NEAR/n' operator with n <= 0");
        
        this.dis = dis;
    }

    
    // Appends an argument to the list of query operator arguments.
    public void add(Qryop q) throws IOException {
        this.args.add(q);
    }

    /** evaluate for operator NEAR/n
     *  the parameter n is stored as 'dis' in this class
     *  @see Qryop#evaluate(RetrievalModel)
     */
    @Override
    public QryResult evaluate(RetrievalModel r) throws IOException {
        
        //  Initialization
        allocDaaTPtrs (r);
        syntaxCheckArgResults (this.daatPtrs);

        QryResult result = new QryResult ();
        
        if (this.daatPtrs.size() == 0)
            return result;
        result.invertedList.field = new String (this.daatPtrs.get(0).invList.field);
        
        // in NEAR/n operator, the order of arguments matter, so let's start
        // from the first argument, and find other arugment in order.
        
        DaaTPtr ptr0 = this.daatPtrs.get(0);
        
        // loop over the postings of the first inverted list
        LOOPOVERFIRSTPOSTING:
        for ( ; ptr0.nextDoc < ptr0.invList.postings.size(); ptr0.nextDoc++) {
            int ptr0DocID = ptr0.invList.getDocid(ptr0.nextDoc);
            
            // loop over other postings to find the same document
            for (int j = 1; j < this.daatPtrs.size(); j++) {
                DaaTPtr ptrj = this.daatPtrs.get(j);
                
                while (true) {
                    if (ptrj.nextDoc >= ptrj.invList.postings.size())
                        break LOOPOVERFIRSTPOSTING;     // no more docs can match
                    
                    if (ptrj.invList.getDocid (ptrj.nextDoc) > ptr0DocID)
                        continue LOOPOVERFIRSTPOSTING;  // the ptr0docid can't match.
                    
                    if (ptrj.invList.getDocid (ptrj.nextDoc) == ptr0DocID)
                        break;                          // match, go to next
                    
                    if (ptrj.invList.getDocid (ptrj.nextDoc) < ptr0DocID)
                        ptrj.nextDoc++;                 // not yet at the right doc.
                }
            }
            
            // reach here if all doc match, go for next stage
            int[] idx = new int[this.daatPtrs.size()]; // index for all files' position
            Vector<Integer> resultPositions = new Vector<Integer>();
            
            // loop over current doc's all positions
            int length = ptr0.invList.getTf(ptr0.nextDoc);
            LOOPOVERFIRSTPOSITIONS:
            for (; idx[0] < length; idx[0]++) {
                int lastPos = ptr0.invList.getPos(ptr0.nextDoc, idx[0]);
                
                for (int j = 1; j < this.daatPtrs.size(); j++) {
                    DaaTPtr ptrj = this.daatPtrs.get(j);
                    int len = ptrj.invList.getTf(ptrj.nextDoc);
                    
                    while (true) {
                        if (idx[j] >= len)
                            break LOOPOVERFIRSTPOSITIONS; // no more match
                        
                        // get the position value of current index
                        int thisPos = ptrj.invList.getPos(ptrj.nextDoc, idx[j]);
                        
                        if (thisPos < lastPos) {
                            idx[j]++;  // later position should be larger
                            continue;
                        }
                        
                        // position info match, continue to next list
                        if (thisPos - lastPos <= dis) {  // ground truth: thisPos != lastPos
                            lastPos = thisPos;
                            break;
                        }
                        else
                            continue LOOPOVERFIRSTPOSITIONS;
                    }                    
                }
                
                // reach here if all positions match, add to temporary result
                // Note: store the position of the last term
                DaaTPtr ptrlast = this.daatPtrs.get(this.daatPtrs.size()-1);
                resultPositions.add(ptrlast.invList.getPos(ptrlast.nextDoc, idx[this.daatPtrs.size()-1]));
                for (int i = 1; i < idx.length; i++)
                    idx[i]++;                
            }
            
            // add this doc to result
            if (!resultPositions.isEmpty()) {
                Collections.sort (resultPositions);
                result.invertedList.appendPosting (ptr0DocID, resultPositions);
            }
        }
        
        freeDaaTPtrs();
        return result;
    }
    
    /**
     *  syntaxCheckArgResults does syntax checking that can only be done
     *  after query arguments are evaluated.
     *  @param ptrs A list of DaaTPtrs for this query operator.
     *  @return True if the syntax is valid, false otherwise.
     */
    public Boolean syntaxCheckArgResults (List<DaaTPtr> ptrs) {

        for (int i=0; i<this.args.size(); i++) {

        if (! (this.args.get(i) instanceof QryopIl))
            QryEval.fatalError ("Error: Invalid argument in " + this.toString());
        
        if ((i>0) && (! ptrs.get(i).invList.field.equals (ptrs.get(0).invList.field)))
            QryEval.fatalError ("Error: Arguments must be in the same field: " +
                   this.toString());
        }
        
        return true;
    }

    /* Return a string version of this query operator.
     * @see Qryop#toString()
     */
    @Override
    public String toString() {
        String result = new String ();

        for (Iterator<Qryop> i = this.args.iterator(); i.hasNext(); )
            result += (i.next().toString() + " ");

        return ("#NEAR/" + dis + "(" + result + ")");
    }

    /**
     * Not use in this operator!
     */
    @Override
    public void addWeight(Double w) {
        // Do nothing        
    }

}
