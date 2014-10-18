import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

public class QryopIlWindow extends QryopIl {

    // the specified window width 'n' in "WINDOW/n" operator
    private int width = 0;

    @Override
    public void add(Qryop q) throws IOException {
        this.args.add(q);
    }

    /**
     * It is convenient for the constructor to accept a variable number of
     * arguments. Thus new QryopIlSyn (arg1, arg2, arg3, ...).
     */
    public QryopIlWindow(int width, Qryop... q) {
        this.width = width;
        
        for (int i = 0; i < q.length; i++)
            this.args.add(q[i]);
    }

    /**
     * This should be the default constructor
     */
    public QryopIlWindow(int width) {
        // distance should greater than 0
        if (width <= 0)
            QryEval.fatalError("'WINDOW/n' operator with n <= 0");

        this.width = width;
    }

    /**
     * evaluate for operator WINDOW/n the parameter n is stored as 'width' in
     * this class
     * 
     * @see Qryop#evaluate(RetrievalModel)
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
        
        // in WINDOW/n operator, the order of arguments doesn't matter, so let's
        // first find the same document and then apply the window
        
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
            
            int maxIdx = -1, minIdx = -1;            
            int maxPos = Integer.MIN_VALUE;
            int minPos = Integer.MAX_VALUE; 
            
            // loop over current doc's all positions
            LOOPOVERPOSITIONS:
            while (true) {
                // find max and min positions
                for (int j = 0; j < this.daatPtrs.size(); j++) {
                    DaaTPtr ptrj = this.daatPtrs.get(j);
                    int len = ptrj.invList.getTf(ptrj.nextDoc);
                    
                    if (idx[j] >= len)
                        break LOOPOVERPOSITIONS;  // no more match
                    
                    // get the position value of current index
                    int thisPos = ptrj.invList.getPos(ptrj.nextDoc, idx[j]);
                    
                    // check min
                    if (thisPos < minPos) {
                        minPos = thisPos;
                        minIdx = j;
                    }
                    
                    // check max
                    if (thisPos > maxPos) {
                        maxPos = thisPos;
                        maxIdx = j;
                    }
                }
                
                // check if match
                if (maxPos - minPos + 1 > width) {// no match
                    idx[minIdx]++;
                    minPos = Integer.MAX_VALUE;
                } else {
                    // add the max position in the result, not sure now
                    DaaTPtr ptrMax = this.daatPtrs
                            .get(maxIdx);
                    resultPositions.add(ptrMax.invList.getPos(ptrMax.nextDoc,
                            idx[maxIdx]));
                    // advance all the indexes and re-initialize max/min position
                    for (int i = 0; i < idx.length; i++)
                        idx[i]++;
                    minPos = Integer.MAX_VALUE;
                    maxPos = Integer.MIN_VALUE;
                }   
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
     * syntaxCheckArgResults does syntax checking that can only be done after
     * query arguments are evaluated.
     * 
     * @param ptrs
     *            A list of DaaTPtrs for this query operator.
     * @return True if the syntax is valid, false otherwise.
     */
    public Boolean syntaxCheckArgResults(List<DaaTPtr> ptrs) {

        for (int i = 0; i < this.args.size(); i++) {

            if (!(this.args.get(i) instanceof QryopIl))
                QryEval.fatalError("Error: Invalid argument in "
                        + this.toString());

            if ((i > 0)
                    && (!ptrs.get(i).invList.field
                            .equals(ptrs.get(0).invList.field)))
                QryEval.fatalError("Error: Arguments must be in the same field: "
                        + this.toString());
        }

        return true;
    }

    /**
     * Return a string version of this query operator.
     * 
     * @see Qryop#toString()
     */
    @Override
    public String toString() {
        String result = new String();

        for (Iterator<Qryop> i = this.args.iterator(); i.hasNext();)
            result += (i.next().toString() + " ");

        return ("#WINDOW/" + width + "(" + result + ")");
    }

    /**
     * Not use in this operator!
     */
    @Override
    public void addWeight(Double w) {
        // Do nothing        
    }

}
