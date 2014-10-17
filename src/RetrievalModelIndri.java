import java.util.Map;

/**
 * The Indri retrieval model has no parameters.
 *
 * @author Jiachen Li (AndrewID: jiachenl)
 */

public class RetrievalModelIndri extends RetrievalModel {
    
    // parameters
    public double mu = 0.0;
    public double lambda = 0.0;
    
    /**
     * Constructor for RetrievalModelIndri
     * 
     * @param  params A map that contains the parameter/value pairs
     */
    public RetrievalModelIndri(Map<String, String> params) {
        // TODO Auto-generated constructor stub
        if (!params.containsKey("Indri:mu") || !params.containsKey("Indri:lambda")) {
            System.err.println("Error: Cannot find the required parameters!");
            System.exit(1);
        }
        
        // set mu
        double val = Double.parseDouble(params.get("Indri:mu"));
        if (val >= 0.0)
            this.mu = val;
        else {
            System.err.println("Error: Wrong parameter value for mu");
            System.exit(1);
        }

        // set lambda
        val = Double.parseDouble(params.get("Indri:lambda"));
        if (val >= 0.0 && val <= 1.0)
            this.lambda = val;
        else {
            System.err.println("Error: Wrong parameter value for lambda");
            System.exit(1);
        }
    }

    /* (non-Javadoc)
     * @see RetrievalModel#setParameter(java.lang.String, double)
     */
    @Override
    public boolean setParameter(String parameterName, double value) {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see RetrievalModel#setParameter(java.lang.String, java.lang.String)
     */
    @Override
    public boolean setParameter(String parameterName, String value) {
        // TODO Auto-generated method stub
        return false;
    }

}
